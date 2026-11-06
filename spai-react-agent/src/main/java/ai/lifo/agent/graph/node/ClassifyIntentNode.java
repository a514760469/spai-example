package ai.lifo.agent.graph.node;

import ai.lifo.agent.graph.EmailClassification;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

/**
 * 分类意图节点
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public class ClassifyIntentNode implements NodeAction {

    private final ChatClient chatClient;

    public ClassifyIntentNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String emailContent = state.value("email_content")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("No email content"));
        String senderEmail = state.value("sender_email")
                .map(v -> (String) v)
                .orElse("unknown");

        // 按需格式化提示，不存储在状态中
        String classificationPrompt = String.format("""
              分析这封客户邮件并进行分类：

              邮件: %s
              发件人: %s

              提供分类，包括意图、紧急程度、主题和摘要。

              意图应该是以下之一: question, bug, billing, feature, complex
              紧急程度应该是以下之一: low, medium, high, critical

              以JSON格式返回: {"intent": "...", "urgency": "...", "topic": "...", "summary": "..."}
              """, emailContent, senderEmail);

        // 获取结构化响应
        String response = chatClient.prompt()
                .user(classificationPrompt)
                .call()
                .content();

        // 解析为 EmailClassification 对象
        EmailClassification classification = parseClassification(response);

        // 根据分类确定下一个节点 账单类或紧急类邮件需要到人工节点
        String nextNode;
        if ("billing".equals(classification.getIntent()) || "critical".equals(classification.getUrgency())) {
            nextNode = "human_review";
        }
        else if (List.of("question", "feature").contains(classification.getIntent())) {
            nextNode = "search_documentation";
        }
        else if ("bug".equals(classification.getIntent())) {
            nextNode = "bug_tracking";
        }
        else {
            nextNode = "draft_response";
        }

        // 将分类作为单个对象存储在状态中
        return Map.of("classification", classification, "next_node", nextNode);
    }

    /**
     * 简化的JSON解析（实际应用中使用Jackson或Gson）
     */
    private EmailClassification parseClassification(String jsonResponse) {
        EmailClassification classification = JSON.parseObject(jsonResponse, EmailClassification.class);

        // 如果解析失败，设置默认值
        if (classification.getIntent() == null) {
            classification.setIntent("question");
        }
        if (classification.getUrgency() == null) {
            classification.setUrgency("medium");
        }
        if (classification.getTopic() == null) {
            classification.setTopic("general");
        }
        if (classification.getSummary() == null) {
            classification.setSummary("需要处理的客户邮件");
        }

        return classification;
    }

}
