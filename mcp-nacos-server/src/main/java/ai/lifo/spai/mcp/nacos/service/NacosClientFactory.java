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
     * SDK 负缓存：记录不支持 ConfigMaintainerService 的连接（如 Nacos 2.x / MSE）。
     * 标记后后续调用直接跳过 SDK 路径，避免重复失败开销。
     */
    private final ConcurrentHashMap<String, Boolean> maintainerUnsupportedCache = new ConcurrentHashMap<>();

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
     * 检查指定连接的 ConfigMaintainerService 是否可用（未被标记为不支持）。
     *
     * @param conn Nacos 连接信息
     * @return true=支持 SDK 方式, false=已被标记为不支持
     */
    public boolean isMaintainerSupported(NacosConnectionInfo conn) {
        return !maintainerUnsupportedCache.containsKey("maintainer:" + buildCacheKey(conn));
    }

    /**
     * 标记指定连接的 ConfigMaintainerService 为不可用。
     * 后续调用 {@link #isMaintainerSupported} 将返回 false，直接跳过 SDK 路径。
     *
     * @param conn Nacos 连接信息
     */
    public void markMaintainerUnsupported(NacosConnectionInfo conn) {
        maintainerUnsupportedCache.put("maintainer:" + buildCacheKey(conn), Boolean.TRUE);
        log.info("[NacosClientFactory] 标记 ConfigMaintainerService 不可用, serverAddr={}, namespace={}",
                conn.serverAddr(), conn.namespace());
    }

    private static @NonNull Properties getProperties(NacosConnectionInfo conn) {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, conn.serverAddr());
        if (isNotBlank(conn.namespace())) {
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
