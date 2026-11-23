package ai.lifo.spai.shopping.creds;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 装饰器，用于在工具调用时注入凭证
 *
 * @author zhanglifeng
 * @since 2026-11-19
 */
@Slf4j
public class CredentialInjectingToolCallback implements StateAwareToolCallback {

    private static final String SESSION_ID = "sessionId";

    static final String NOT_LOGGED_IN = """
                {"status":"error","code":"NOT_LOGGED_IN","message":"用户尚未登录。请先询问用户手机号并调用 loginByPhoneNumber 完成登录，然后重试本操作。"}
            """;

    private final ToolCallback delegate;
    private final CredentialStore credentialStore;
    private final ObjectMapper objectMapper;
    private final ToolDefinition sanitizedDefinition;
    private final boolean requiresSession;


    public CredentialInjectingToolCallback(ToolCallback delegate,
                                           CredentialStore credentialStore,
                                           ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.credentialStore = credentialStore;
        this.objectMapper = objectMapper;
        ToolDefinition original = delegate.getToolDefinition();
        try {
            // schema 是静态的，构造时裁剪一次即可
            ObjectNode schema = (ObjectNode) objectMapper.readTree(original.inputSchema());
            ObjectNode properties = (ObjectNode) schema.path("properties");
            this.requiresSession = properties.has(SESSION_ID);
            if (requiresSession) {
                properties.remove(SESSION_ID);
                if (schema.path("required").isArray()) {
                    ArrayNode required = (ArrayNode) schema.path("required");
                    for (int i = required.size() - 1; i >= 0; i--) {
                        if (SESSION_ID.equals(required.get(i).asText())) {
                            required.remove(i);
                        }
                    }
                }
            }
            this.sanitizedDefinition = DefaultToolDefinition.builder()
                    .name(original.name())
                    .description(original.description())
                    .inputSchema(objectMapper.writeValueAsString(schema))
                    .build();
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("无法裁剪工具 schema: " + original.name(), e);
        }
    }


    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.sanitizedDefinition;
    }

    @Override
    public String call(String toolInput) {
        if (!requiresSession) {
            return unwrapResult(delegate.call(toolInput));
        }
        throw new UnsupportedOperationException("必须走带 ToolContext 的 call，否则无法注入凭证");
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        if (!requiresSession) {
            String result = delegate.call(toolInput);
            return unwrapResult(result);
        }

        RunnableConfig config = (RunnableConfig) toolContext.getContext()
                .get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);

        String threadId = config.threadId().orElseThrow(() -> new IllegalStateException("缺少 threadId"));

        Optional<CredentialStore.Credential> credential = credentialStore.find(threadId);

        if (credential.isEmpty()) {
            return NOT_LOGGED_IN;   // 未登录/已过期都走这里，模型会引导重新登录
        }

        try {
            ObjectNode args = (ObjectNode) objectMapper.readTree(StringUtils.hasText(toolInput) ? toolInput : "{}");
            args.put(SESSION_ID, credential.get().sessionId());
            String result = delegate.call(objectMapper.writeValueAsString(args));
            return unwrapResult(result);
        }
        catch (JsonProcessingException e) {
            throw new ToolExecutionException(getToolDefinition(), e);
        }
    }



    @JsonIgnoreProperties(ignoreUnknown = true)
    private record McpContent(String type, String text) {}

    /**
     * 剥掉 MCP 返回的消息壳，节省token
     * - 不是 content 数组形态，原样透传
     */
    private String unwrapResult(String raw) {
        // log.info("原始结果: {}", raw);
        McpContent[] contents;
        try {
            contents = objectMapper.readValue(raw, McpContent[].class);
        }
        catch (Exception e) {
            return raw;
        }

        if (contents.length == 0 || contents[0].text() == null) {
            return raw;
        }

        String text = contents[0].text();
        try {
            Object payload = objectMapper.readValue(text, Object.class);
            // 双重编码 → payload 是 String，本身就是干净 JSON 文档；单重编码 → Map/List，重新序列化
            return payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        }
        catch (Exception e) {
            return text;
        }
    }
}
