package ai.lifo.agent.graph.email;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *  起草回复节点
 * @author zhanglifeng
 * @since 2026-11-03
 */
@Slf4j
public class DraftResponseNode implements NodeAction {

    private final ChatClient chatClient;

    public DraftResponseNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        EmailClassification classification = state.value("classification")
                .map(v -> (EmailClassification) v)
                .orElse(new EmailClassification());

        String emailContent = state.value("email_content")
                .map(v -> (String) v)
                .orElse("");
        // 从原始状态数据按需格式化上下文
        List<String> contextSections = new ArrayList<>();
        Optional<List<String>> searchResults = state.value("search_results")
                .map(v -> (List<String>) v);
        if (searchResults.isPresent()) {
            // 为提示格式化搜索结果
            List<String> docs = searchResults.get();
            String formattedDocs = docs.stream()
                    .map(doc -> "- " + doc)
                    .collect(Collectors.joining("\n"));
            contextSections.add("相关文档:\n" + formattedDocs);
        }

        Optional<Map<String, Object>> customerHistory = state.value("customer_history")
                .map(v -> (Map<String, Object>) v);
        // 为提示格式化客户数据
        customerHistory.ifPresent(history -> {
            contextSections.add("客户等级: " + history.getOrDefault("tier", "standard"));
        });

        // 使用格式化的上下文构建提示
        String draftPrompt = String.format("""
					为这封客户邮件起草回复:
					%s

					邮件意图: %s
					紧急程度: %s

					%s

					指南:
					- 专业且有帮助
					- 解决他们的具体问题
					- 在相关时使用提供的文档
					""",
                emailContent,
                classification.getIntent(),
                classification.getUrgency(),
                String.join("\n", contextSections)
        );

        String response = chatClient.prompt()
                .user(draftPrompt)
                .call()
                .content();

        // 根据紧急程度和意图确定是否需要人工审核
        boolean needsReview =
                List.of("high", "critical").contains(classification.getUrgency()) ||
                        "complex".equals(classification.getIntent());

        // 路由到适当的下一个节点
        String nextNode = needsReview ? "human_review" : "send_reply";

        return Map.of(
                "draft_response", response,  // 仅存储原始响应
                "next_node", nextNode
        );

    }
}
