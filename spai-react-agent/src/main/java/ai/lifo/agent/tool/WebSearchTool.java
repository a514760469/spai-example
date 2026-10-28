package ai.lifo.agent.tool;

import ai.lifo.agent.tool.serper.SerperErrorResponse;
import ai.lifo.agent.tool.serper.SerperSearchResponse;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Google 搜索工具（基于 Serper API）。
 * <p>
 * 只做一件事：把查询词转成「带编号、带链接、已裁剪」的 markdown 交给模型。
 * 请求与响应都走强类型 record，失败也返回结构化对象而不是抛异常 —— 抛异常会中断 ReAct 循环。
 *
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public class WebSearchTool {

    private static final String DEFAULT_BASE_URL = "https://google.serper.dev";

    private static final int MIN_RESULTS = 1;

    private static final int MAX_RESULTS = 10;

    private static final int DEFAULT_RESULTS = 5;

    /** 单条摘要最大字符数，防止一条长摘要挤掉其余结果 */
    private static final int SNIPPET_MAX_CHARS = 400;

    /** 单次返回给模型的总字符预算 */
    private static final int RESPONSE_MAX_CHARS = 4000;

    private static final String TYPE_SEARCH = "search";

    private static final String TYPE_NEWS = "news";

    private final RestClient restClient;

    private final String apiKey;

    private final ObjectMapper objectMapper;

    public WebSearchTool(RestClient.Builder builder) {
        this(builder, resolveApiKey(), new ObjectMapper());
    }

    public WebSearchTool(RestClient.Builder builder, String apiKey) {
        this(builder, apiKey, new ObjectMapper());
    }

    public WebSearchTool(RestClient.Builder builder, String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(DEFAULT_BASE_URL).build();
    }

    /**
     * @param query      搜索词，尽量写完整、可独立成立的查询
     * @param maxResults 返回条数，1~10，默认 5
     * @param type       检索类型：search（网页，默认）/ news（新闻）
     * @param timeRange  时间范围：day / week / month / year，可不填
     */
    public record Request(String query, Integer maxResults, String type, String timeRange) {}

    /**
     * @param success  是否成功
     * @param markdown 给模型看的正文（成功时是搜索结果，失败时是可读的失败原因）
     * @param error    失败时的结构化错误，成功时为 null
     */
    public record Response(boolean success, String markdown, Error error) {

        static Response ok(String markdown) {
            return new Response(true, markdown, null);
        }

        static Response fail(Error error) {
            return new Response(false, "搜索失败：" + error.message(), error);
        }
    }

    /**
     * @param code     机器可读的错误码
     * @param message  可读描述
     * @param upstream Serper 返回的原始错误对象，可能为 null（网络异常时没有响应体）
     */
    public record Error(String code, String message, SerperErrorResponse upstream) {}

    public Response search(Request req) {
        if (req == null || !StringUtils.hasText(req.query())) {
            return Response.fail(new Error("EMPTY_QUERY", "query 不能为空", null));
        }
        if (!StringUtils.hasText(apiKey)) {
            return Response.fail(new Error("MISSING_API_KEY",
                    "未配置 SERPER_API_KEY（环境变量或 JVM 参数 -DAI_SERPER_API_KEY）", null));
        }

        String query = req.query().trim();
        int num = req.maxResults() == null ? DEFAULT_RESULTS
                : Math.clamp(req.maxResults(), MIN_RESULTS, MAX_RESULTS);
        String type = TYPE_NEWS.equalsIgnoreCase(req.type()) ? TYPE_NEWS : TYPE_SEARCH;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("gl", "cn");
        body.put("hl", "zh-cn");
        body.put("num", num);
        String tbs = toTbs(req.timeRange());
        if (tbs != null) {
            body.put("tbs", tbs);
        }

        log.info("请求body{}", JSONObject.toJSONString(body));
        long start = System.currentTimeMillis();
        try {
            SerperSearchResponse response = restClient.post()
                    .uri("/" + type)
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SerperSearchResponse.class);

            if (response == null) {
                return Response.fail(new Error("EMPTY_RESPONSE", "Serper 返回空响应", null));
            }
            log.info("响应结果：{}", JSONObject.toJSONString(response));
            log.info("[WebSearchTool] query={} type={} credits={} 耗时={}ms", query, type, response.credits(), System.currentTimeMillis() - start);
            return Response.ok(render(response, query, type, num));

        } catch (RestClientResponseException e) {
            Error error = toError(e);
            log.warn("[WebSearchTool] Serper 调用失败: code={} message={}", error.code(), error.message());
            return Response.fail(error);

        } catch (Exception e) {
            log.warn("[WebSearchTool] 搜索异常: {}", e.toString());
            return Response.fail(new Error("NETWORK_ERROR",
                    "网络异常（" + e.getClass().getSimpleName() + "），请稍后重试或更换关键词", null));
        }
    }

    /** 把 Serper 的错误响应体解析成对象，解析不出来就退化成状态码描述 */
    private Error toError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String code = switch (status) {
            case 401, 403 -> "API_KEY_INVALID";
            case 429 -> "RATE_LIMITED";
            case 400 -> "BAD_REQUEST";
            default -> status >= 500 ? "SERPER_SERVER_ERROR" : "HTTP_" + status;
        };

        SerperErrorResponse upstream = null;
        String raw = e.getResponseBodyAsString();
        if (StringUtils.hasText(raw)) {
            try {
                upstream = objectMapper.readValue(raw, SerperErrorResponse.class);
            } catch (Exception ignore) {
                // 错误体不是 JSON（如网关返回 HTML），保留原始片段在 message 里
            }
        }

        String detail = upstream != null && StringUtils.hasText(upstream.message())
                ? upstream.message()
                : abbreviate(raw, 200);
        String message = switch (code) {
            case "API_KEY_INVALID" -> "API Key 无效或额度已用尽";
            case "RATE_LIMITED" -> "请求过于频繁，已被限流，稍后重试";
            case "BAD_REQUEST" -> "请求参数被拒绝";
            case "SERPER_SERVER_ERROR" -> "Serper 服务端异常，稍后重试";
            default -> "Serper 返回异常状态";
        } + "（HTTP " + status + (StringUtils.hasText(detail) ? "：" + detail : "") + "）";

        return new Error(code, message, upstream);
    }

    /**
     * 把强类型响应渲染成模型友好的 markdown：
     * 直接答案 → 知识卡片 → 带编号的结果列表 → 相关搜索
     */
    private String render(SerperSearchResponse response, String query, String type, int maxResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索「").append(query).append("」（google/serper，")
                .append(TYPE_NEWS.equals(type) ? "新闻" : "网页").append("）\n\n");

        // 1. 直接答案（Google 的部分查询会直接给出，优先级最高）
        SerperSearchResponse.AnswerBox answerBox = response.answerBox();
        if (answerBox != null) {
            String answer = firstText(answerBox.answer(), answerBox.snippet());
            if (StringUtils.hasText(answer)) {
                sb.append("[直接答案] ").append(truncate(answer, SNIPPET_MAX_CHARS)).append('\n');
                if (StringUtils.hasText(answerBox.link())) {
                    sb.append("来源：").append(answerBox.link()).append('\n');
                }
                sb.append('\n');
            }
        }

        // 2. 知识卡片
        SerperSearchResponse.KnowledgeGraph knowledge = response.knowledgeGraph();
        if (knowledge != null && StringUtils.hasText(knowledge.description())) {
            sb.append("[知识卡片] ")
                    .append(truncate(nvl(knowledge.title()), 60)).append("：")
                    .append(truncate(knowledge.description(), SNIPPET_MAX_CHARS)).append('\n');
            if (StringUtils.hasText(knowledge.descriptionLink())
                    && knowledge.descriptionLink().startsWith("http")) {
                sb.append("来源：").append(knowledge.descriptionLink()).append('\n');
            }
            sb.append('\n');
        }

        // 3. 结果列表：organic 与 news 字段不同，先归一成一个内部结构再渲染
        List<Item> items = TYPE_NEWS.equals(type) ? newsItems(response.news()) : organicItems(response.organic());
        if (items.isEmpty()) {
            sb.append("没有检索到结果。建议换个更具体的说法，或去掉时间范围限制后重试。\n");
            appendRelated(sb, response);
            return sb.toString();
        }

        Set<String> seenLinks = new HashSet<>();
        int index = 1;
        boolean hitNumLimit = false;
        boolean hitCharLimit = false;
        for (Item item : items) {
            // news 端点不认 num 参数，会一次返回十几条，必须自己按 num 截断
            if (index > maxResults) {
                hitNumLimit = true;
                break;
            }
            if (StringUtils.hasText(item.link()) && !seenLinks.add(item.link())) {
                continue;   // 同一批结果里重复的链接直接丢掉
            }
            String block = renderItem(index, item);
            if (sb.length() + block.length() > RESPONSE_MAX_CHARS) {
                hitCharLimit = true;
                break;
            }
            sb.append(block);
            index++;
        }

        if (index == 1) {
            sb.append("没有检索到结果。建议换个更具体的说法，或去掉时间范围限制后重试。\n");
        } else if (hitCharLimit) {
            sb.append("（已达长度上限，仅展示前 ").append(index - 1).append(" 条）\n");
        } else if (hitNumLimit) {
            sb.append("（共 ").append(items.size()).append(" 条，按 maxResults 只取前 ")
                    .append(index - 1).append(" 条）\n");
        }

        // 4. 相关搜索：模型据此判断是否需要二次检索
        appendRelated(sb, response);
        return sb.toString();
    }

    private String renderItem(int index, Item item) {
        StringBuilder b = new StringBuilder();
        b.append('[').append(index).append("] ").append(truncate(nvl(item.title()), 150));

        List<String> meta = new ArrayList<>(2);
        if (StringUtils.hasText(item.date())) {
            meta.add(item.date());
        }
        if (StringUtils.hasText(item.source())) {
            meta.add(item.source());
        }
        if (!meta.isEmpty()) {
            b.append("（").append(String.join(" · ", meta)).append("）");
        }
        b.append('\n');

        if (StringUtils.hasText(item.link())) {
            b.append(item.link()).append('\n');
        }
        if (StringUtils.hasText(item.snippet())) {
            b.append(truncate(item.snippet(), SNIPPET_MAX_CHARS)).append('\n');
        }
        b.append('\n');
        return b.toString();
    }

    private void appendRelated(StringBuilder sb, SerperSearchResponse response) {
        List<SerperSearchResponse.RelatedSearch> related = response.relatedSearches();
        if (related == null || related.isEmpty()) {
            return;
        }
        List<String> queries = new ArrayList<>();
        for (SerperSearchResponse.RelatedSearch r : related) {
            if (r != null && StringUtils.hasText(r.query())) {
                queries.add(r.query());
            }
        }
        if (!queries.isEmpty()) {
            sb.append("[相关搜索] ").append(String.join(" / ", queries)).append('\n');
        }
    }

    /** organic / news 的公共视图；news 没有 snippet，字段为 null */
    private record Item(String title, String link, String snippet, String date, String source) {}

    private static List<Item> organicItems(List<SerperSearchResponse.OrganicResult> results) {
        List<Item> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        for (SerperSearchResponse.OrganicResult r : results) {
            if (r != null) {
                items.add(new Item(r.title(), r.link(), r.snippet(), r.date(), null));
            }
        }
        return items;
    }

    private static List<Item> newsItems(List<SerperSearchResponse.NewsResult> results) {
        List<Item> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        for (SerperSearchResponse.NewsResult r : results) {
            if (r != null) {
                items.add(new Item(r.title(), r.link(), null, r.date(), r.source()));
            }
        }
        return items;
    }

    /** day/week/month/year → Google 的 tbs 语法；已经是 qdr:* 的直接透传 */
    private static String toTbs(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            return null;
        }
        String r = timeRange.trim().toLowerCase();
        if (r.startsWith("qdr:")) {
            return r;
        }
        return switch (r) {
            case "day", "d", "1d" -> "qdr:d";
            case "week", "w", "1w" -> "qdr:w";
            case "month", "m", "1m" -> "qdr:m";
            case "year", "y", "1y" -> "qdr:y";
            default -> null;
        };
    }

    private static String firstText(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String abbreviate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "…");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 依次尝试：环境变量 SERPER_API_KEY → AI_SERPER_API_KEY → JVM 参数 */
    private static String resolveApiKey() {
        String key = System.getenv("SERPER_API_KEY");
        if (!StringUtils.hasText(key)) {
            key = System.getenv("AI_SERPER_API_KEY");
        }
        if (!StringUtils.hasText(key)) {
            key = System.getProperty("AI_SERPER_API_KEY");
        }
        if (!StringUtils.hasText(key)) {
            log.warn("[WebSearchTool] 未找到 SERPER_API_KEY，联网搜索将不可用");
        }
        return key;
    }
}
