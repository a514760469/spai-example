package ai.lifo.spai.shopping.tool;

import ai.lifo.spai.shopping.creds.CredentialStore;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-11-19
 */
@Slf4j
@Component
public class LoginTool {

    private final CredentialStore credentialStore;

    private final ToolCallbackProvider mcpProvider;

    private final ObjectMapper objectMapper;

    private final String platformSecret;

    public LoginTool(CredentialStore credentialStore,
                     ToolCallbackProvider mcpProvider,
                     ObjectMapper objectMapper,
                     @Value("${shopping.mock.platform-secret}") String platformSecret) {
        this.credentialStore = credentialStore;
        this.mcpProvider = mcpProvider;
        this.objectMapper = objectMapper;
        this.platformSecret = platformSecret;
    }

    @Tool(description = "用手机号登录电商平台。用户报出手机号后直接调用，系统自动完成认证，无需其他凭证。")
    public String loginByPhoneNumber(String phoneNumber, ToolContext ctx) {
        if (!StringUtils.hasText(phoneNumber)) {
            return "{\"status\":\"error\",\"message\":\"手机号不能为空\"}";
        }

        RunnableConfig config = (RunnableConfig) ctx.getContext()
                .get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);

        String threadId = config.threadId().orElseThrow(() -> new IllegalStateException("缺少 threadId"));
        try {
            ToolCallback loginCallback = Arrays.stream(mcpProvider.getToolCallbacks())
                    .filter(cb -> cb.getToolDefinition().name().equals("serverToServerLogin"))
                    .findFirst()
                    .orElseThrow();

            String args = objectMapper.writeValueAsString(Map.of(
                    "phoneNumber", phoneNumber, "platform", "shopping-agent", "secret", platformSecret));

            // SyncMcpToolCallback.call 返回的是 content 数组 [{"type":"text","text":...}]，不是工具文本本身
            McpContent[] contents = objectMapper.readValue(loginCallback.call(args), McpContent[].class);
            // 服务端 @Tool 返回 String 会被 Spring AI 再包一层 JSON 字符串（双重编码），两种形态都兼容
            Object payload = objectMapper.readValue(contents[0].text(), Object.class);
            LoginResult resp = payload instanceof String s
                    ? objectMapper.readValue(s, LoginResult.class)
                    : objectMapper.convertValue(payload, LoginResult.class);

            if (!"success".equals(resp.status())) {
                return objectMapper.writeValueAsString(
                        Map.of("status", "error", "message", resp.error() != null ? resp.error() : "登录失败")
                );
            }

            // 服务端容器是 UTC，expiresAt 是无时区的 LocalDateTime 字符串，必须按 UTC 解析
            long expiresAt = LocalDateTime.parse(resp.expiresAt())
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();

            credentialStore.save(threadId, resp.sessionId(), phoneNumber, expiresAt);

            log.info("login success, thread={}", threadId);   // 不 log sessionId

            return "{\"status\":\"success\",\"message\":\"登录成功，后续操作无需再提供凭证\"}";
        }
        catch (Exception e) {
            log.error("login failed, thread={}", threadId, e);
            return "{\"status\":\"error\",\"message\":\"登录失败，请稍后重试\"}";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record McpContent(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LoginResult(String status, String sessionId, String expiresAt, String error) {
    }
}
