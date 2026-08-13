package ai.lifo.spai.mcp.nacos.service;

import ai.lifo.spai.mcp.nacos.model.GetConfigRequest;
import ai.lifo.spai.mcp.nacos.model.ListConfigsRequest;
import ai.lifo.spai.mcp.nacos.model.NacosConnectionInfo;
import ai.lifo.spai.mcp.nacos.model.SearchConfigRequest;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.model.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos 配置读取 MCP Tool 服务。
 * <p>
 * 所有 Nacos 连接参数（serverAddr、namespace、username、password、accessKey、secretKey）均由调用方动态传入。
 * 支持两种鉴权方式：用户名/密码 和 AccessKey/SecretKey。
 * </p>
 *
 * @author zhanglifeng
 * @since 2026-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosConfigToolService {

    private final NacosClientFactory nacosClientFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final long TOKEN_TTL_MS = 30 * 60 * 1000L;

    /**
     * accessToken 缓存条目，包含 token 值和过期时间。
     */
    private record TokenEntry(String token, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * accessToken 缓存，key = serverAddr:username
     */
    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    // ======================== Tool: getConfig ========================

    @Tool(description = "Get the content of a specific Nacos configuration by dataId and group.")
    public String getConfig(GetConfigRequest req) {
        var conn = new NacosConnectionInfo(req.serverAddr(),
                req.namespace(),
                req.username(),
                req.password(),
                req.accessKey(),
                req.secretKey());

        log.info("[MCP Tool] 获取配置内容, serverAddr={}, namespace={}, dataId={}, group={}",
                req.serverAddr(), req.namespace(), req.dataId(), req.group());

        // 优先尝试 SDK 方式（ConfigMaintainerService）
        if (nacosClientFactory.isMaintainerSupported(conn)) {
            try {
                return getConfigViaMaintainer(conn, req.dataId(), req.group());
            } catch (Exception e) {
                nacosClientFactory.markMaintainerUnsupported(conn);
                log.warn("[MCP Tool] SDK 方式获取配置失败, 回退到基础方式, dataId={}, group={}, error={}",
                        req.dataId(), req.group(), e.getMessage());
            }
        }

        // 回退到 ConfigService 基础方式
        try {
            ConfigService configService = nacosClientFactory.getConfigService(conn);
            String content = configService.getConfig(req.dataId(), req.group(), 5000);
            if (content == null) {
                log.warn("[MCP Tool] 配置未找到, dataId={}, group={}, namespace={}", req.dataId(), req.group(), req.namespace());
                return String.format("Config not found: dataId=%s, group=%s, namespace=%s", req.dataId(), req.group(), req.namespace());
            }
            log.info("[MCP Tool] 配置获取成功（基础方式）, dataId={}, group={}, 内容长度={}", req.dataId(), req.group(), content.length());
            return content;
        } catch (Exception e) {
            log.error("[MCP Tool] 获取配置失败, dataId={}, group={}, serverAddr={}", req.dataId(), req.group(), req.serverAddr(), e);
            return "Error: " + e.getMessage();
        }
    }

    // ======================== Tool: listConfigs ========================

    @Tool(description = "List Nacos configurations in a specific namespace and group, with pagination.")
    public String listConfigs(ListConfigsRequest req) {
        log.info("[MCP Tool] 列出配置列表, serverAddr={}, namespace={}, group={}, pageNo={}, pageSize={}",
                req.serverAddr(), req.namespace(), req.group(), req.pageNo(), req.pageSize());

        var conn = new NacosConnectionInfo(req.serverAddr(),
                req.namespace(),
                req.username(),
                req.password(),
                req.accessKey(),
                req.secretKey());

        // 优先尝试 SDK 方式
        if (nacosClientFactory.isMaintainerSupported(conn)) {
            try {
                return listConfigsViaMaintainer(conn, req.group(), req.pageNo(), req.pageSize());
            } catch (Exception e) {
                nacosClientFactory.markMaintainerUnsupported(conn);
                log.warn("[MCP Tool] SDK 方式列出配置失败, 回退到 HTTP 方式, group={}, error={}", req.group(), e.getMessage());
            }
        }

        // 回退到 HTTP 方式
        try {
            return queryConfigs(conn, req.group(), "", "accurate", req.pageNo(), req.pageSize());
        } catch (Exception e) {
            log.error("[MCP Tool] HTTP 方式列出配置也失败, group={}, serverAddr={}", req.group(), req.serverAddr(), e);
            return "Error: " + e.getMessage();
        }
    }

    // ======================== Tool: searchConfig ========================

    @Tool(description = "Search Nacos configurations by fuzzy matching dataId keyword, with pagination.")
    public String searchConfig(SearchConfigRequest req) {
        log.info("[MCP Tool] 模糊搜索配置, serverAddr={}, namespace={}, search={}, group={}, pageNo={}, pageSize={}",
                req.serverAddr(), req.namespace(), req.search(), req.group(), req.pageNo(), req.pageSize());

        var conn = new NacosConnectionInfo(req.serverAddr(),
                req.namespace(),
                req.username(),
                req.password(),
                req.accessKey(),
                req.secretKey());

        String search = req.search() == null ? "" : req.search();

        // 优先尝试 SDK 方式
        if (nacosClientFactory.isMaintainerSupported(conn)) {
            try {
                return searchConfigViaMaintainer(conn, req.group(), search, req.pageNo(), req.pageSize());
            } catch (Exception e) {
                nacosClientFactory.markMaintainerUnsupported(conn);
                log.warn("[MCP Tool] SDK 方式搜索配置失败, 回退到 HTTP 方式, search={}, error={}", search, e.getMessage());
            }
        }

        // 回退到 HTTP 方式
        try {
            return queryConfigs(conn, req.group(), search, "blur", req.pageNo(), req.pageSize());
        } catch (Exception e) {
            log.error("[MCP Tool] HTTP 方式搜索配置也失败, search={}, serverAddr={}", search, req.serverAddr(), e);
            return "Error: " + e.getMessage();
        }
    }

    // ======================== SDK 方式（nacos-maintainer-client）========================

    /**
     * 通过 ConfigMaintainerService SDK 获取配置详情。
     * 返回裸配置内容，与 fallback 路径格式保持一致。
     */
    private String getConfigViaMaintainer(NacosConnectionInfo conn, String dataId, String group) throws Exception {
        var maintainer = nacosClientFactory.getConfigMaintainerService(conn);
        String namespace = normalizeNamespace(conn.namespace());
        log.info("[MCP Tool/SDK] 调用 getConfig, namespace={}, dataId={}, group={}", namespace, dataId, group);
        ConfigDetailInfo detail = maintainer.getConfig(namespace, dataId, group);
        if (detail == null || detail.getContent() == null) {
            log.warn("[MCP Tool/SDK] 配置未找到, dataId={}, group={}, namespace={}", dataId, group, namespace);
            return String.format("Config not found: dataId=%s, group=%s, namespace=%s", dataId, group, namespace);
        }
        log.info("[MCP Tool/SDK] getConfig 成功, dataId={}, group={}, 内容长度={}", dataId, group, detail.getContent().length());
        return detail.getContent();
    }

    /**
     * 通过 ConfigMaintainerService SDK 列出配置。
     * 内置鉴权、重试、多节点 failover，替代旧的 HTTP URL 拼接方式。
     */
    private String listConfigsViaMaintainer(NacosConnectionInfo conn, String group, int pageNo, int pageSize) throws Exception {
        var maintainer = nacosClientFactory.getConfigMaintainerService(conn);
        String namespace = normalizeNamespace(conn.namespace());
        log.info("[MCP Tool/SDK] 调用 listConfigs, namespace={}, group={}, pageNo={}, pageSize={}", namespace, group, pageNo, pageSize);
        Page<ConfigBasicInfo> page = maintainer.listConfigs(namespace, group, "", "", "", "", pageNo, pageSize);
        log.info("[MCP Tool/SDK] listConfigs 成功, 总数={}, 当前页条数={}", page.getTotalCount(), page.getPageItems().size());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(page);
    }

    /**
     * 通过 ConfigMaintainerService SDK 搜索配置。
     * 内置鉴权、重试、多节点 failover，替代旧的 HTTP URL 拼接方式。
     */
    private String searchConfigViaMaintainer(NacosConnectionInfo conn, String group, String search, int pageNo, int pageSize) throws Exception {
        var maintainer = nacosClientFactory.getConfigMaintainerService(conn);
        String namespace = normalizeNamespace(conn.namespace());
        log.info("[MCP Tool/SDK] 调用 searchConfigs, namespace={}, group={}, search={}, pageNo={}, pageSize={}", namespace, group, search, pageNo, pageSize);
        Page<ConfigBasicInfo> page = maintainer.searchConfigs(namespace, group, search, "", "", "", "", pageNo, pageSize);
        log.info("[MCP Tool/SDK] searchConfigs 成功, 总数={}, 当前页条数={}", page.getTotalCount(), page.getPageItems().size());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(page);
    }

    // ======================== Internal Helpers（旧 HTTP 方式）========================

    /**
     * 配置查询公共方法，listConfigs 和 searchConfig 共用。
     *
     * @param searchType accurate=精确匹配, blur=模糊匹配
     * @param dataId     搜索关键字（accurate 时为空，blur 时为搜索词）
     */
    private String queryConfigs(NacosConnectionInfo conn,
                                String group,
                                String dataId,
                                String searchType,
                                int pageNo,
                                int pageSize) {
        try {
            String baseUrl = buildBaseUrl(conn.serverAddr());
            String tenant = normalizeNamespace(conn.namespace());
            String accessToken = getAccessToken(conn);

            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/nacos/v1/cs/configs?search=").append(searchType)
                    .append("&dataId=").append(encode(dataId == null ? "" : dataId))
                    .append("&group=").append(encode(group == null ? "" : group))
                    .append("&tenant=").append(encode(tenant))
                    .append("&pageNo=").append(pageNo)
                    .append("&pageSize=").append(pageSize);

            if (accessToken != null && !accessToken.isBlank()) {
                url.append("&accessToken=").append(encode(accessToken));
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .GET()
                    .timeout(Duration.ofSeconds(10));
            addAkSkHeaders(requestBuilder, "GET", "/nacos/v1/cs/configs", "", conn);

            String result = executeHttpGet(requestBuilder);
            log.info("[MCP Tool] 配置查询成功, searchType={}, group={}, pageNo={}, pageSize={}", searchType, group, pageNo, pageSize);
            return result;
        } catch (Exception e) {
            log.error("[MCP Tool] 配置查询失败, searchType={}, group={}, serverAddr={}", searchType, group, conn.serverAddr(), e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 获取 Nacos accessToken（用户名/密码鉴权用），带 TTL 缓存。
     * 若使用 AK/SK 鉴权或无用户名，则返回 null（通过请求头或无鉴权方式访问）。
     * 登录失败时不缓存，下次调用会重试。
     */
    private String getAccessToken(NacosConnectionInfo conn) {
        if (isNotBlank(conn.accessKey())) {
            log.debug("[Nacos Token] 使用 AccessKey/SecretKey 鉴权, 跳过 accessToken 获取, serverAddr={}", conn.serverAddr());
            return null;
        }
        if (!isNotBlank(conn.username())) {
            log.debug("[Nacos Token] 未提供用户名和 AccessKey, 跳过鉴权, serverAddr={}", conn.serverAddr());
            return null;
        }
        String cacheKey = conn.serverAddr() + ":" + conn.username();
        TokenEntry cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("[Nacos Token] 复用已有 accessToken, serverAddr={}, username={}", conn.serverAddr(), conn.username());
            return cached.token();
        }
        // 缓存过期或不存在，重新获取
        try {
            log.info("[Nacos Token] 正在获取 accessToken, serverAddr={}, username={}", conn.serverAddr(), conn.username());
            String baseUrl = buildBaseUrl(conn.serverAddr());
            String body = "username=" + encode(conn.username()) + "&password=" + encode(conn.password() == null ? "" : conn.password());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/nacos/v1/auth/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String token = json.path("accessToken").asText("");
                if (!token.isBlank()) {
                    tokenCache.put(cacheKey, new TokenEntry(token, System.currentTimeMillis() + TOKEN_TTL_MS));
                    log.info("[Nacos Token] accessToken 获取成功, serverAddr={}, username={}", conn.serverAddr(), conn.username());
                    return token;
                }
            }
            log.warn("[Nacos Token] 登录失败, serverAddr={}, username={}, status={}, body={}",
                    conn.serverAddr(), conn.username(), response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("[Nacos Token] 获取 accessToken 异常, serverAddr={}, username={}", conn.serverAddr(), conn.username(), e);
        }
        return null;
    }

    /**
     * 为 HTTP 请求添加 AK/SK 签名头。
     * 仅在 accessKey 和 secretKey 都非空时添加。
     */
    private void addAkSkHeaders(HttpRequest.Builder builder,
                                String method,
                                String path,
                                String body,
                                NacosConnectionInfo conn) {
        if (!isNotBlank(conn.accessKey()) || !isNotBlank(conn.secretKey())) {
            return;
        }
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString();
            String bodyMd5 = md5(body == null ? "" : body);

            // 签名串: HTTP方法 + "\n" + BodyMD5 + "\n" + ContentType + "\n" + Timestamp + "\n" + CanonicalURI
            String stringToSign = method + "\n"
                    + bodyMd5 + "\n"
                    + "" + "\n"
                    + timestamp + "\n"
                    + path;

            String signature = hmacSha256(conn.secretKey(), stringToSign);

            builder.header("X-Nacos-Access-Key", conn.accessKey());
            builder.header("X-Nacos-Signature", signature);
            builder.header("X-Nacos-Signature-Method", "HMACSHA256");
            builder.header("X-Nacos-Signature-Nonce", nonce);
            builder.header("X-Nacos-Signature-Timestamp", timestamp);
            log.debug("[Nacos AK/SK] 已添加签名头, accessKey={}, path={}", conn.accessKey(), path);
        } catch (Exception e) {
            log.warn("[Nacos AK/SK] 签名计算失败, accessKey={}", conn.accessKey(), e);
        }
    }

    private String hmacSha256(String secretKey, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signBytes);
    }

    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private String executeHttpGet(HttpRequest.Builder requestBuilder) throws Exception {
        HttpRequest request = requestBuilder.build();
        log.debug("[Nacos HTTP] 发送 GET 请求: {}", request.uri());

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("[Nacos HTTP] 响应状态码: {}, 响应长度: {}", response.statusCode(), response.body().length());
        if (response.statusCode() != 200) {
            log.warn("[Nacos HTTP] 请求失败, 状态码: {}, 响应: {}", response.statusCode(), response.body());
            return String.format("HTTP %d: %s", response.statusCode(), response.body());
        }

        // 尝试格式化 JSON 输出
        try {
            JsonNode json = objectMapper.readTree(response.body());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return response.body();
        }
    }

    private String buildBaseUrl(String serverAddr) {
        if (serverAddr.startsWith("http://") || serverAddr.startsWith("https://")) {
            return serverAddr;
        }
        return "http://" + serverAddr;
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isBlank() || "public".equalsIgnoreCase(namespace)) {
            return "";
        }
        return namespace;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
