package ai.lifo.spai.example.config;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springframework.stereotype.Component;

/**
 * @author zhanglifeng
 * @since 2026-05-12
 */
@Slf4j
@Component
public class McpClientHandlers {

    @McpLogging(clients = "mcp-auth-client")
    public void handleLogs(@NotNull McpSchema.LoggingMessageNotification notification) {
        log.info("handleLogs: {}", notification.data());
    }

    @McpSampling(clients = "mcp-auth-client")
    public McpSchema.CreateMessageResult handleSampling(@NotNull McpSchema.CreateMessageRequest request) {
        log.info("handleSampling: {}", request.messages());
        return McpSchema.CreateMessageResult.builder()
                .message("Generated response")
                .build();
    }

    @McpProgress(clients = "mcp-auth-client")
    public void handleProgress(McpSchema.ProgressNotification notification) {
        log.info("handleProgress: {}", notification);
    }

}
