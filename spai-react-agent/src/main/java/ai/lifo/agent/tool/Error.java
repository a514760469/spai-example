package ai.lifo.agent.tool;

import ai.lifo.agent.tool.serper.SerperErrorResponse;

/**
 * @param code     机器可读的错误码
 * @param message  可读描述
 * @param upstream Serper 返回的原始错误对象，可能为 null（网络异常时没有响应体）
 */
public record Error(String code, String message, SerperErrorResponse upstream) {
}
