package ai.credithc.xd.late;

import com.alibaba.cloud.nacos.utils.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.client.McpClient;
import org.springframework.ai.mcp.client.McpSyncClient;
import org.springframework.ai.mcp.client.stdio.ServerParameters;
import org.springframework.ai.mcp.client.stdio.StdioClientTransport;
import org.springframework.ai.mcp.spring.McpFunctionCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

/**
 * @author zhanglifeng
 * @since 2025-03-20
 */
@SpringBootApplication
public class SpringAIMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAIMcpApplication.class, args);
    }


    @Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder,
                                                 List<McpFunctionCallback> functionCallbacks,
                                                 ConfigurableApplicationContext context) {
        return _ -> {
            var chatClient = chatClientBuilder
                    .defaultTools(functionCallbacks.toArray(new McpFunctionCallback[0]))
                    .build();

            System.out.println("Running predefined questions with AI model responses:\n");

            // Question 1
            String question1 = "Can you explain the content of the spring-ai-mcp-overview.txt file?";
            System.out.println("QUESTION: " + question1);
            System.out.println("ASSISTANT: " + chatClient.prompt(question1).call().content());

            // Question 2
            String question2 = "Pleses summarize the content of the spring-ai-mcp-overview.txt file and store it a new summary.md as Markdown format?";
            System.out.println("QUESTION: " + question2);
            System.out.println("ASSISTANT: " + chatClient.prompt(question2).call().content());
            context.close();

        };
    }


    @Bean
    public List<McpFunctionCallback> functionCallbacks(McpSyncClient mcpClient) {

        return mcpClient.listTools().tools().stream().map(tool -> new McpFunctionCallback(mcpClient, tool)).toList();
    }

    @Bean(destroyMethod = "close")
    public McpSyncClient mcpClient() {

        // based on
        // https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
        String filePath = getDbPath();
        System.err.println(STR. "filePath: \{filePath}");
        var stdioParams = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", filePath)
                .build();

        var mcpClient = McpClient.using(new StdioClientTransport(stdioParams))
                .requestTimeout(Duration.ofSeconds(10)).sync();

        var init = mcpClient.initialize();

        System.out.println(STR."MCP Initialized: \{init}");

        return mcpClient;

    }

    static String getFilePath() {
        String path = System.getenv("spring-ai-mcp-overview.txt");
        return StringUtils.isEmpty(path) ? getDbPath() : path;
    }

    static String getDbPath() {
        return Paths.get(STR."\{System.getProperty("user.dir")}").toString();
    }
}
