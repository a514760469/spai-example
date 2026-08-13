package ai.lifo.spai.mcp.nacos.model;

/**
 * Nacos 连接与鉴权信息，封装公共参数。
 * 支持两种鉴权方式：用户名/密码 和 AccessKey/SecretKey，对应字段可为空。
 *
 * @author zhanglifeng
 * @since 2026-06-30
 */
public record NacosConnectionInfo(
        String serverAddr,
        String namespace,
        String username,
        String password,
        String accessKey,
        String secretKey) {
}
