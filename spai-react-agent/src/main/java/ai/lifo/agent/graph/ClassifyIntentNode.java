package ai.lifo.agent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public class ClassifyIntentNode implements NodeAction {

    private final ChatClient chatClient;

    public ClassifyIntentNode(ChatClient chatClient) {
        this.chatClient = chatClient;
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

        // 根据分类确定下一个节点
        String nextNode;
        if ("billing".equals(classification.getIntent()) || "critical".equals(classification.getUrgency())) {
            nextNode = "human_review";
        } else if (List.of("question", "feature").contains(classification.getIntent())) {
            nextNode = "search_documentation";
        } else if ("bug".equals(classification.getIntent())) {
            nextNode = "bug_tracking";
        } else {
            nextNode = "draft_response";
        }

        // 将分类作为单个对象存储在状态中
        return Map.of("classification", classification, "next_node", nextNode);
    }

    /**
     * 简化的JSON解析（实际应用中使用Jackson或Gson）
     */
    private EmailClassification parseClassification(String jsonResponse) {
        EmailClassification classification = new EmailClassification();

        // 简单的正则表达式解析
        Pattern intentPattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
        Pattern urgencyPattern = Pattern.compile("\"urgency\"\\s*:\\s*\"([^\"]+)\"");
        Pattern topicPattern = Pattern.compile("\"topic\"\\s*:\\s*\"([^\"]+)\"");
        Pattern summaryPattern = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"");

        Matcher matcher = intentPattern.matcher(jsonResponse);
        if (matcher.find()) {
            classification.setIntent(matcher.group(1));
        }

        matcher = urgencyPattern.matcher(jsonResponse);
        if (matcher.find()) {
            classification.setUrgency(matcher.group(1));
        }

        matcher = topicPattern.matcher(jsonResponse);
        if (matcher.find()) {
            classification.setTopic(matcher.group(1));
        }

        matcher = summaryPattern.matcher(jsonResponse);
        if (matcher.find()) {
            classification.setSummary(matcher.group(1));
        }

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
