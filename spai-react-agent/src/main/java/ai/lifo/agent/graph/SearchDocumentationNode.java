package ai.lifo.agent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public class SearchDocumentationNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 从分类构建搜索查询
        EmailClassification classification = state.value("classification")
                .map(v -> (EmailClassification) v)
                .orElse(new EmailClassification());
        String query = classification.getIntent() + " " + classification.getTopic();

        try {
            // 实现您的搜索逻辑
            // 存储原始搜索结果，而不是格式化的文本
            List<String> searchResults = List.of(
                    "通过设置 > 安全 > 更改密码重置密码",
                    "密码必须至少12个字符",
                    "包含大写字母、小写字母、数字和符号"
            );

            log.info("Searching documentation for: {}", query);

            return Map.of(
                    "search_results", searchResults,
                    "next_node", "draft_response"
            );
        } catch (Exception e) {
            // 对于可恢复的搜索错误，存储错误并继续
            log.warn("Search error: {}", e.getMessage());
            List<String> errorResult = List.of("搜索暂时不可用: " + e.getMessage());
            return Map.of(
                    "search_results", errorResult,
                    "next_node", "draft_response"
            );
        }
    }
}
