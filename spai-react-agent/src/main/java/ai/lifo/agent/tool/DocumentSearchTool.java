package ai.lifo.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 创建文档检索工具
 *
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public record DocumentSearchTool(VectorStore vectorStore) {

    public record Request(String query) {
    }

    public record Response(List<String> content) {
    }

    public Response search(Request request) {
        // 从向量存储检索相关文档
        List<Document> docs = vectorStore.similaritySearch(request.query());

        log.info("检索到内容：{}", docs.size());
        // 合并文档内容
        List<String> combinedContent = docs.stream()
                .map(Document::getText)
                .collect(Collectors.toList());

        return new Response(combinedContent);
    }
}
