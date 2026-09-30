package ai.lifo.spai.chat.config;

import ai.lifo.spai.chat.custom.MybatisChatMemoryRepository;
import ai.lifo.spai.chat.mapper.CustomChatMemoryMapper;
import com.alibaba.cloud.ai.memory.jdbc.MysqlChatMemoryRepository;
import com.alibaba.cloud.ai.memory.redis.LettuceRedisChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author zhanglifeng
 * @since 2026-09-25
 */
@Configuration
public class ChatConfig {

//    @Bean
//    public ChatClient dashScopeChatClient(ChatModel dashScopeChatModel) {
//
//        return ChatClient.builder(dashScopeChatModel)
//                .defaultSystem("尽可能直接给出答案")
//                .defaultAdvisors(new SimpleLoggerAdvisor())
//                .defaultOptions(DashScopeChatOptions.builder()
//                        .topP(0.7)
//                        .build())
//                .build();
//    }

    @Bean
    public MysqlChatMemoryRepository mysqlChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return MysqlChatMemoryRepository.mysqlBuilder()
                .jdbcTemplate(jdbcTemplate)
                .build();
    }

    @Bean
    public LettuceRedisChatMemoryRepository lettuceRedisChatMemoryRepository() {
        return LettuceRedisChatMemoryRepository.builder()
                .host("127.0.0.1")
                .port(6379)
                .password("123456")
                .database(3)
                .build();
    }

    @Bean
    public MybatisChatMemoryRepository mybatisChatMemoryRepository(CustomChatMemoryMapper customChatMemoryMapper) {
        return MybatisChatMemoryRepository.mysqlBuilder()
                .service(customChatMemoryMapper)
                .build();
    }

}
