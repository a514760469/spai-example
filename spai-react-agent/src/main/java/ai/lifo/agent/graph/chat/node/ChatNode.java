package ai.lifo.agent.graph.chat.node;

import ai.lifo.agent.config.UserPreferences;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 聊天节点
 * @author zhanglifeng
 * @since 2026-11-05
 */
public class ChatNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "chat";

    /** 流式输出结果写入的 state key（独立于 messages，避免污染对话历史类型） */
    public static final String OUTPUT_KEY = "answer";

    private final ChatClient chatClient;

    public ChatNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem("你是小智，性别女，作为一个助手，回答用户问题，语气温柔，说话通俗，让人喜欢。")
                .build();
    }


    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {

        List<UserMessage> messages = (List<UserMessage>) state.value("messages").orElse(List.of());

        UserPreferences userPreferences = (UserPreferences) state.value(UserPreferences.KEY).orElse(UserPreferences.DEFAULT);

        // 构建包含用户偏好的提示
        String userPrompt = messages.getLast().getText();
        String enhancedPrompt = "用户偏好: " + userPreferences.preferences() + " 用户问题: " + userPrompt;

        // 调用 AI
        Flux<ChatResponse> chatResponse = chatClient.prompt()
                .user(enhancedPrompt)
                .stream()
                .chatResponse();

        return Map.of(ChatNode.OUTPUT_KEY, chatResponse);
        // Flux 必须作为返回 Map 的顶层 value，框架才会自动订阅、逐 token 输出，
        //  并在流结束后把聚合结果写回 State（否则 Flux 会被 checkpoint 序列化，触发反射异常）
//        return Map.of("messages", chatResponse);
    }
}
