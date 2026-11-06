package ai.lifo.agent.tool.serper;

import ai.lifo.agent.tool.Error;

/**
 * @param success  是否成功
 * @param markdown 给模型看的正文（成功时是搜索结果，失败时是可读的失败原因）
 * @param error    失败时的结构化错误，成功时为 null
 */
public record Response(boolean success, String markdown, Error error) {

    public static Response ok(String markdown) {
        return new Response(true, markdown, null);
    }

    public static Response fail(Error error) {
        return new Response(false, "搜索失败：" + error.message(), error);
    }
}
