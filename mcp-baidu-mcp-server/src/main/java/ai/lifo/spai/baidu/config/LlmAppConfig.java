package ai.lifo.spai.baidu.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-09-17
 */
@Configuration
public class LlmAppConfig {


    @Bean
    public ChatClient chatClient(ChatModel chatModel) {

        return ChatClient.builder(chatModel)
                .build();
    }


}
