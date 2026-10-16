package ai.lifo.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-10-15
 */
@Configuration
public class ContextAwareAgentConfig {


    @Bean
    public ReactAgent contextAwareAgent(ChatModel chatModel) {
        // 使用拦截器创建Agent
        return ReactAgent.builder()
                .name("context_aware_agent")
                .model(chatModel)
                .interceptors(new StateAwarePromptInterceptor())
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
}
