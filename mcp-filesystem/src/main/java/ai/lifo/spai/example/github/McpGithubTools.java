package ai.lifo.spai.example.github;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-05-15
 */
@Configuration
public class McpGithubTools {

    @Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder,
                                                 ToolCallbackProvider tools,
                                                 ConfigurableApplicationContext context) {

        return args -> {
            // 构建ChatClient 并注入mcp工具
            var chatClient = chatClientBuilder
                    .defaultToolCallbacks(tools)
                    .build();

            // Question 1
            String question1 = "帮我创建一个私有仓库命名为: test-mcp";
            System.out.println("QUESTION: " + question1);
            // 调用 LLM 并打印结果
            String callResult = chatClient.prompt(question1).call().content();
            System.out.println("ASSISTANT: " + callResult);

            context.close();
        };
    }
}
