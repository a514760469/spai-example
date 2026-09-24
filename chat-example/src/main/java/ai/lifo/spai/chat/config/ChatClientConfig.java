package ai.lifo.spai.chat.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-09-18
 */
@Configuration
public class ChatClientConfig {

    /**
     * 配置ChatClient
     * top_k Range: (0, 100) 默认null禁用 Larger values increase randomness; smaller values increase determinism.
     * top_p Range: (0, 1.0), default: 0.8. Higher values increase randomness; lower values increase determinism.
     */
    @Bean
    public ChatClient dashScopeChatClient(ChatModel dashScopeChatModel) {

        return ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(DashScopeChatOptions.builder()
                        .topP(0.7)
                        .build())
                .build();
    }


    /**
     * 配置 deepSeekChatClient
     */
    @Bean
    public ChatClient deepSeekChatClient(ChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(DeepSeekChatOptions.builder().temperature(0.7).build())
                .build();
    }


    /**
     * 配置 ollamaChatClient
     */
    @Bean
    public ChatClient ollamaChatClient(ChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(OllamaChatOptions.builder().temperature(0.8).build())
                .build();
    }


}
