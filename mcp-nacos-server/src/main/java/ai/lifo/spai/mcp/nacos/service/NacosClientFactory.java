package ai.lifo.spai.mcp.nacos.service;

import ai.lifo.spai.mcp.nacos.model.NacosConnectionInfo;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerFactory;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos 客户端工厂类，根据传入的连接参数动态创建并缓存实例。
 * 支持 ConfigService（配置读写）和 ConfigMaintainerService（配置管理）两种客户端。
 * 支持两种鉴权方式：用户名/密码 和 AccessKey/SecretKey。
 *
 * @author zhanglifeng
 * @since 2026-06-30
 */
@Slf4j
@Component
public class NacosClientFactory {

    private final ConcurrentHashMap<String, ConfigService> cache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ConfigMaintainerService> maintainerCache = new ConcurrentHashMap<>();

    /**
     * SDK 负缓存默认有效期：5 分钟。到期后自动重新尝试 SDK，让服务端升级、网络恢复等情况能够自愈。
     */
    private static final long DEFAULT_MAINTAINER_UNSUPPORTED_TTL_MS = 5 * 60 * 1000L;

    /**
     * 负缓存有效期（毫秒），可通过 {@code nacos.maintainer.unsupported-ttl-ms} 调整。
     */
    @Value("${nacos.maintainer.unsupported-ttl-ms:300000}")
    private long maintainerUnsupportedTtlMs = DEFAULT_MAINTAINER_UNSUPPORTED_TTL_MS;

    /**
     * SDK 负缓存：记录「某个连接的某个请求」上一次被判定为不支持 ConfigMaintainerService（如 Nacos 2.x / MSE）。
     * <p>
     * 粒度是请求级（连接 + 操作 + 目标），不是连接级：查一条不存在的配置不该连累同连接上的列表与搜索。
     * 条目带过期时间，到期即失效；任一次 SDK 调用成功会立即清除对应条目。
     * </p>
     */
    private final ConcurrentHashMap<String, UnsupportedEntry> maintainerUnsupportedCache = new ConcurrentHashMap<>();

    /**
     * 负缓存条目，只保存过期时间戳。
     */
    private record UnsupportedEntry(long expireAt) {
        boolean isExpired(long now) {
            return now >= expireAt;
        }
    }

    /**
     * 根据连接信息获取或创建 ConfigService 实例。
     * 支持两种鉴权方式：
     * 1. 用户名/密码：conn 中包含 username 和 password
     * 2. AccessKey/SecretKey：conn 中包含 accessKey 和 secretKey
     *
     * @param conn Nacos 连接与鉴权信息
     * @return ConfigService 实例
     */
    public ConfigService getConfigService(NacosConnectionInfo conn) {

        String cacheKey = buildCacheKey(conn);
        return cache.computeIfAbsent(cacheKey, key -> {
            try {
                Properties properties = getProperties(conn);

                String authType = resolveAuthType(conn);
                log.info("[NacosClientFactory] 正在创建 Nacos ConfigService 连接, serverAddr={}, namespace={}, 鉴权方式={}",
                        conn.serverAddr(), conn.namespace(), authType);
                ConfigService configService = NacosFactory.createConfigService(properties);
                log.info("[NacosClientFactory] Nacos ConfigService 连接创建成功, serverAddr={}, namespace={}", conn.serverAddr(), conn.namespace());
                return configService;
            } catch (NacosException e) {
                log.error("[NacosClientFactory] Nacos ConfigService 连接创建失败, serverAddr={}, namespace={}", conn.serverAddr(), conn.namespace(), e);
                throw new RuntimeException("创建 Nacos ConfigService 失败: " + conn.serverAddr(), e);
            }
        });
    }

    /**
     * 根据连接信息获取或创建 ConfigMaintainerService 实例。
     * 用于配置管理操作（listConfigs、searchConfigs 等），内置鉴权和重试机制。
     *
     * @param conn Nacos 连接与鉴权信息
     * @return ConfigMaintainerService 实例
     */
    public ConfigMaintainerService getConfigMaintainerService(NacosConnectionInfo conn) {
        String cacheKey = "maintainer:" + buildCacheKey(conn);
        return maintainerCache.computeIfAbsent(cacheKey, key -> {
            try {
                Properties properties = getProperties(conn);
                String authType = resolveAuthType(conn);
                log.info("[NacosClientFactory] 正在创建 ConfigMaintainerService, serverAddr={}, namespace={}, 鉴权方式={}",
                        conn.serverAddr(), conn.namespace(), authType);
                ConfigMaintainerService service = ConfigMaintainerFactory.createConfigMaintainerService(properties);
                log.info("[NacosClientFactory] ConfigMaintainerService 创建成功, serverAddr={}, namespace={}",
                        conn.serverAddr(), conn.namespace());
                return service;
            } catch (NacosException e) {
                log.error("[NacosClientFactory] ConfigMaintainerService 创建失败, serverAddr={}, namespace={}",
                        conn.serverAddr(), conn.namespace(), e);
                throw new RuntimeException("创建 ConfigMaintainerService 失败: " + conn.serverAddr(), e);
            }
        });
    }

    /**
     * 检查指定连接上某个请求的 ConfigMaintainerService 是否可用（未被标记为不支持，或标记已过期）。
     *
     * @param conn  Nacos 连接信息
     * @param scope 请求级标识，见 NacosConfigToolService 中的 getScope/listScope/searchScope
     * @return true=支持 SDK 方式, false=该请求已被标记为不支持且未过期
     */
    public boolean isMaintainerSupported(NacosConnectionInfo conn, String scope) {
        String key = maintainerCacheKey(conn, scope);
        UnsupportedEntry entry = maintainerUnsupportedCache.get(key);
        if (entry == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (entry.isExpired(now)) {
            maintainerUnsupportedCache.remove(key, entry);
            log.info("[NacosClientFactory] 负缓存已过期，重新尝试 ConfigMaintainerService, serverAddr={}, namespace={}, scope={}",
                    conn.serverAddr(), conn.namespace(), scope);
            return true;
        }
        return false;
    }

    /**
     * 标记指定连接上的某个请求不支持 ConfigMaintainerService。
     * 该标记只影响同一连接 + 同一 scope，且带有效期，到期自动重新尝试。
     *
     * @param conn  Nacos 连接信息
     * @param scope 请求级标识
     */
    public void markMaintainerUnsupported(NacosConnectionInfo conn, String scope) {
        long now = System.currentTimeMillis();
        maintainerUnsupportedCache.put(maintainerCacheKey(conn, scope), new UnsupportedEntry(now + maintainerUnsupportedTtlMs));
        log.info("[NacosClientFactory] 标记 ConfigMaintainerService 不可用, serverAddr={}, namespace={}, scope={}, ttl={}ms",
                conn.serverAddr(), conn.namespace(), scope, maintainerUnsupportedTtlMs);
        purgeExpired(now);
    }

    /**
     * 清除指定连接上某个请求的负缓存标记。SDK 调用成功后调用，使系统可以自愈。
     *
     * @param conn  Nacos 连接信息
     * @param scope 请求级标识
     */
    public void clearMaintainerUnsupported(NacosConnectionInfo conn, String scope) {
        String key = maintainerCacheKey(conn, scope);
        if (maintainerUnsupportedCache.remove(key) != null) {
            log.info("[NacosClientFactory] 清除 ConfigMaintainerService 负缓存, serverAddr={}, namespace={}, scope={}",
                    conn.serverAddr(), conn.namespace(), scope);
        }
    }

    /**
     * 清理已过期的负缓存条目，避免大量一次性请求把 map 撑大。
     */
    private void purgeExpired(long now) {
        maintainerUnsupportedCache.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    private String maintainerCacheKey(NacosConnectionInfo conn, String scope) {
        return "maintainer:" + buildCacheKey(conn) + ":" + nullToEmpty(scope);
    }

    @NonNull
    private static Properties getProperties(NacosConnectionInfo conn) {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, conn.serverAddr());
        // 默认命名空间（空 / public）不设置 NAMESPACE：
        // 2.x 客户端的默认命名空间 id 是空串，传 "public" 会被当成名为 public 的命名空间
        if (isExplicitNamespace(conn.namespace())) {
            properties.put(PropertyKeyConst.NAMESPACE, conn.namespace());
        }
        // 用户名/密码 鉴权
        if (isNotBlank(conn.username())) {
            properties.put(PropertyKeyConst.USERNAME, conn.username());
        }
        if (isNotBlank(conn.password())) {
            properties.put(PropertyKeyConst.PASSWORD, conn.password());
        }
        // AccessKey/SecretKey 鉴权
        if (isNotBlank(conn.accessKey())) {
            properties.put(PropertyKeyConst.ACCESS_KEY, conn.accessKey());
        }
        if (isNotBlank(conn.secretKey())) {
            properties.put(PropertyKeyConst.SECRET_KEY, conn.secretKey());
        }
        return properties;
    }

    private String resolveAuthType(NacosConnectionInfo conn) {
        if (isNotBlank(conn.accessKey())) {
            return "AccessKey/SecretKey";
        }
        if (isNotBlank(conn.username())) {
            return "用户名/密码";
        }
        return "无鉴权";
    }

    private String buildCacheKey(NacosConnectionInfo conn) {
        return String.join(":",
                nullToEmpty(conn.serverAddr()),
                nullToEmpty(conn.namespace()),
                nullToEmpty(conn.username()),
                nullToEmpty(conn.accessKey()));
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 是否为显式指定的命名空间（空串 / "public" 均视为默认命名空间）。
     */
    private static boolean isExplicitNamespace(String namespace) {
        return isNotBlank(namespace) && !"public".equalsIgnoreCase(namespace);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
