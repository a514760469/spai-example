package ai.lifo.spai.example.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-10-08
 */
@Configuration
public class RagConfiguration {

    //    @Bean
    //    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
    //
    //        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    //    }
//    @Bean
//    public LettuceRedisChatMemoryRepository lettuceRedisChatMemoryRepository() {
//        return LettuceRedisChatMemoryRepository.builder()
//                .host("127.0.0.1")
//                .port(6379)
//                .password("123456")
//                .database(3)
//                .build();
//    }

    @Bean
    public ChatMemory chatMemory() {
        //
        return MessageWindowChatMemory.builder().build();
    }
}
