package ai.lifo.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.Getter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-15
 */
@Configuration
public class ContextAwareAgentConfig {


    @Bean
    public ReactAgent contextAwareAgent(ChatModel chatModel) {
        UserPreferenceStore store = new UserPreferenceStore();
        store.savePreferences("user-001", new UserPreferences("友好轻松", "中文", List.of("技术", "阅读")));

        // 使用拦截器创建Agent
        return ReactAgent.builder()
                .name("context_aware_agent")
                .model(chatModel)
                .interceptors(new StateAwarePromptInterceptor(), new PersonalizedPromptInterceptor(store))
                .build();
    }



    static class StateAwarePromptInterceptor extends ModelInterceptor {

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            List<Message> messages = request.getMessages();
            int messageCount = messages.size();

            // 基础提示
            String basePrompt = "你是一个有用的助手。";

            // 根据消息数量调整提示
            if (messageCount > 10) {
                basePrompt += " 这是一个长对话 - 请尽量保持精准简捷。";
            }

            // 更新系统消息（参考 TodoListInterceptor 的实现方式）
            SystemMessage enhancedSystemMessage;
            if (request.getSystemMessage() == null) {
                enhancedSystemMessage = new SystemMessage(basePrompt);
            } else {
                enhancedSystemMessage = new SystemMessage(request.getSystemMessage().getText() + " " + basePrompt);
            }

            // 创建增强的请求
            ModelRequest enhancedRequest = ModelRequest.builder(request)
                    .systemMessage(enhancedSystemMessage)
                    .build();

            // 调用处理器
            return handler.call(enhancedRequest);
        }

        @Override
        public String getName() {
            return "stateAwarePromptInterceptor";
        }
    }

    // 简单的用户偏好存储
    static class UserPreferenceStore {
        private final Map<String, UserPreferences> store = new HashMap<>();

        public UserPreferences getPreferences(String userId) {
            return store.getOrDefault(userId, new UserPreferences("专业", "中文", List.of()));
        }

        public void savePreferences(String userId, UserPreferences prefs) {
            store.put(userId, prefs);
        }
    }

    // 用户偏好类
    @Getter
    static class UserPreferences {
        private final String communicationStyle;
        private final String language;
        private final List<String> interests;

        public UserPreferences(String style, String lang, List<String> interests) {
            this.communicationStyle = style;
            this.language = lang;
            this.interests = interests;
        }

    }

    /**
     * 基于存储的个性化提示
     * 从长期记忆加载用户偏好并生成个性化提示
     */
    class PersonalizedPromptInterceptor extends ModelInterceptor {
        private final UserPreferenceStore store;

        public PersonalizedPromptInterceptor(UserPreferenceStore store) {
            this.store = store;
        }

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            // 从运行时上下文获取用户ID
            String userId = getUserIdFromContext(request);

            // 从存储加载用户偏好
            UserPreferences prefs = store.getPreferences(userId);

            // 构建个性化提示
            String personalizedPrompt = buildPersonalizedPrompt(prefs);

            // 更新系统消息（参考 TodoListInterceptor 的实现方式）
            SystemMessage enhancedSystemMessage;
            if (request.getSystemMessage() == null) {
                enhancedSystemMessage = new SystemMessage(personalizedPrompt);
            }
            else {
                enhancedSystemMessage = new SystemMessage(
                        request.getSystemMessage().getText() + "\n\n" + personalizedPrompt
                );
            }

            // 创建增强的请求
            ModelRequest enhancedRequest = ModelRequest.builder(request)
                    .systemMessage(enhancedSystemMessage)
                    .build();

            // 调用处理器
            return handler.call(enhancedRequest);
        }

        private String getUserIdFromContext(ModelRequest request) {
            // 从请求上下文提取用户ID
//            return "user_001"; // 简化示例
            // 相当于是从 RunnableConfig 中读取提取用户ID，所以agent调用时要设置 user-id
            return request.getContext().get("user_id").toString(); // 简化示例
        }

        private String buildPersonalizedPrompt(UserPreferences prefs) {
            StringBuilder prompt = new StringBuilder("你是一个有用的助手。");

            if (prefs.getCommunicationStyle() != null) {
                prompt.append("\n沟通风格：").append(prefs.getCommunicationStyle());
            }

            if (prefs.getLanguage() != null) {
                prompt.append("\n使用语言：").append(prefs.getLanguage());
            }

            if (!prefs.getInterests().isEmpty()) {
                prompt.append("\n用户兴趣：").append(String.join(", ", prefs.getInterests()));
            }

            return prompt.toString();
        }

        @Override
        public String getName() {
            return "PersonalizedPromptInterceptor";
        }
    }

}
