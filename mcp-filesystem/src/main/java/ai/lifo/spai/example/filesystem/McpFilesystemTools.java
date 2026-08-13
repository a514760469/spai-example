package ai.lifo.spai.example.filesystem;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * @author zhanglifeng
 * @since 2026-05-15
 */
//@Configuration
public class McpFilesystemTools {

    @Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder,
                                                 McpSyncClient mcpClient,
                                                 ConfigurableApplicationContext context) {

        return args -> {
            var chatClient = chatClientBuilder
                    .defaultToolCallbacks(SyncMcpToolCallbackProvider.builder().mcpClients(mcpClient).build())
                    .build();

            System.out.println("Running predefined questions with AI model responses:\n");

            // Question 1
            String question1 = "Can you explain the content of the target/spring-ai-mcp-overview.txt file?";
            System.out.println("QUESTION: " + question1);
            System.out.println("ASSISTANT: " + chatClient.prompt(question1).call().content());

            // Question 2
//            String question2 = "Please summarize the content of the target/spring-ai-mcp-overview.txt file and store it a new target/summary.md as Markdown format?";
            String question2 = "请总结目标文件“spring-ai-mcp-overview.txt”的内容，并将其以 Markdown 格式保存为新的文件“target/summary.md”";
            System.out.println("\nQUESTION: " + question2);
            System.out.println("ASSISTANT: " + chatClient.prompt(question2).call().content());

            context.close();
        };
    }

    @Bean(destroyMethod = "close")
    public McpSyncClient mcpClient() {

        // based on
        // https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
        // Windows 系统需要改为 npx.cmd
        var stdioParams = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", getDbPath())
                .build();

        var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams, McpJsonMapper.getDefault()))
                .requestTimeout(Duration.ofSeconds(10)).build();

        var init = mcpClient.initialize();

        System.out.println("MCP Initialized: " + init);

        return mcpClient;
    }

    private static String getDbPath() {
        // mcp-filesystem/target
        // windows use: spring-ai-alibaba-mcp-example/spring-ai-alibaba-manual-mcp-example/ai-mcp-fileserver/target
        String path = Paths.get(System.getProperty("user.dir"), "target").toString();
        System.out.println(path);
        return path;
    }

}
