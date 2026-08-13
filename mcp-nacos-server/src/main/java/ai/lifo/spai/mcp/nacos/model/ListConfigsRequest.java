package ai.lifo.spai.mcp.nacos.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 列出 Nacos 配置列表的请求参数。
 *
 * @author zhanglifeng
 * @since 2026-06-30
 */
public record ListConfigsRequest(
        @JsonPropertyDescription("Nacos server address, e.g. localhost:8848") String serverAddr,
        @JsonPropertyDescription("Nacos namespace ID, empty or 'public' for default namespace") String namespace,
        @JsonPropertyDescription("Nacos username for username/password auth, can be empty") String username,
        @JsonPropertyDescription("Nacos password for username/password auth, can be empty") String password,
        @JsonPropertyDescription("Nacos AccessKey for AK/SK auth, can be empty") String accessKey,
        @JsonPropertyDescription("Nacos SecretKey for AK/SK auth, can be empty") String secretKey,
        @JsonPropertyDescription("Configuration group to filter, e.g. DEFAULT_GROUP") String group,
        @JsonPropertyDescription("Page number, starting from 1") int pageNo,
        @JsonPropertyDescription("Page size, e.g. 20") int pageSize
) {
}
