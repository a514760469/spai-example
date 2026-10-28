package ai.lifo.agent.tool.serper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Serper 的错误响应：{"message":"Unauthorized.","statusCode":403}
 *
 * @author zhanglifeng
 * @since 2026-10-28
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SerperErrorResponse(String message, Integer statusCode) {
}
