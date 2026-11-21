package ai.lifo.spai.shopping.creds;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CredentialInjectingToolCallback 纯单测（不起 Spring）：
 * schema 裁剪、凭证注入、NOT_LOGGED_IN 契约、结果解包（双重/单重编码、非 JSON、非数组）。
 * delegate 与 CredentialStore 用 Mockito，ObjectMapper 用真实实例。
 */
class CredentialInjectingToolCallbackTest {

    private static final String AUTH_SCHEMA = """
            {"type":"object","properties":{"productId":{"type":"string"},"quantity":{"type":"integer"},"sessionId":{"type":"string"}},"required":["productId","quantity","sessionId"],"additionalProperties":false}""";

    private static final String OPEN_SCHEMA = """
            {"type":"object","properties":{"query":{"type":"string"}},"required":["query"],"additionalProperties":false}""";

    private static final String INNER_JSON = "{\"status\":\"success\",\"cartId\":\"c-1\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolCallback delegate = mock(ToolCallback.class);
    private final CredentialStore credentialStore = mock(CredentialStore.class);

    private CredentialInjectingToolCallback callback(String schema) {
        when(delegate.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("addToCart").description("test tool").inputSchema(schema).build());
        return new CredentialInjectingToolCallback(delegate, credentialStore, objectMapper);
    }

    private String contentArray(String text) throws Exception {
        return objectMapper.writeValueAsString(List.of(Map.of("type", "text", "text", text)));
    }

    private ToolContext context(String threadId) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        return new ToolContext(Map.of(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY, config));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    // ---------- 结果解包 ----------

    @Test
    void unwrapDoubleEncodedText() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        when(delegate.call(anyString())).thenReturn(contentArray(objectMapper.writeValueAsString(INNER_JSON)));

        assertEquals(INNER_JSON, cb.call("{}", context("t1")));
    }

    @Test
    void unwrapSingleEncodedText() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        when(delegate.call(anyString())).thenReturn(contentArray(INNER_JSON));

        assertEquals(INNER_JSON, cb.call("{}", context("t1")));
    }

    @Test
    void unwrapNonJsonTextReturnsText() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        when(delegate.call(anyString())).thenReturn(contentArray("OK"));

        assertEquals("OK", cb.call("{}", context("t1")));
    }

    @Test
    void unwrapNonArrayRawPassesThrough() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        String raw = "{\"a\":1}";
        when(delegate.call(anyString())).thenReturn(raw);

        assertEquals(raw, cb.call("{}", context("t1")));
    }

    @Test
    void unwrapEmptyContentArrayPassesThrough() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        when(delegate.call(anyString())).thenReturn("[]");

        assertEquals("[]", cb.call("{}", context("t1")));
    }

    // ---------- schema 裁剪 ----------

    @Test
    @SuppressWarnings("unchecked")
    void stripsSessionIdFromSchema() throws Exception {
        CredentialInjectingToolCallback cb = callback(AUTH_SCHEMA);
        ToolDefinition def = cb.getToolDefinition();

        assertEquals("addToCart", def.name());
        assertEquals("test tool", def.description());

        Map<String, Object> schema = parse(def.inputSchema());
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertFalse(properties.containsKey("sessionId"));
        assertTrue(properties.containsKey("productId"));
        List<String> required = (List<String>) schema.get("required");
        assertEquals(List.of("productId", "quantity"), required);
    }

    @Test
    void openSchemaKeepsAllProperties() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);

        assertEquals(parse(OPEN_SCHEMA), parse(cb.getToolDefinition().inputSchema()));
    }

    @Test
    void toolMetadataDelegates() {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        ToolMetadata metadata = ToolMetadata.builder().build();
        when(delegate.getToolMetadata()).thenReturn(metadata);

        assertSame(metadata, cb.getToolMetadata());
    }

    // ---------- 单参 call 契约 ----------

    @Test
    void singleArgCallOnAuthToolThrows() {
        CredentialInjectingToolCallback cb = callback(AUTH_SCHEMA);

        assertThrows(UnsupportedOperationException.class, () -> cb.call("{}"));
    }

    @Test
    void singleArgCallOnOpenToolDelegatesAndUnwraps() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        when(delegate.call(anyString())).thenReturn(contentArray(INNER_JSON));

        assertEquals(INNER_JSON, cb.call("{}"));
    }

    // ---------- 凭证注入 ----------

    @Test
    void notLoggedInReturnsStructuredErrorWithoutCallingServer() {
        CredentialInjectingToolCallback cb = callback(AUTH_SCHEMA);
        when(credentialStore.find("t1")).thenReturn(Optional.empty());

        String result = cb.call("{\"productId\":\"p-1\"}", context("t1"));

        assertTrue(result.contains("NOT_LOGGED_IN"));
        assertTrue(result.contains("loginByPhoneNumber"));
        verify(delegate, never()).call(anyString());
    }

    @Test
    void injectsSessionIdAndUnwrapsResult() throws Exception {
        CredentialInjectingToolCallback cb = callback(AUTH_SCHEMA);
        when(credentialStore.find("t1"))
                .thenReturn(Optional.of(new CredentialStore.Credential("sid-123", "13800001111")));
        when(delegate.call(anyString())).thenReturn(contentArray(objectMapper.writeValueAsString(INNER_JSON)));

        String result = cb.call("{\"productId\":\"p-1\",\"quantity\":2}", context("t1"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(delegate).call(captor.capture());
        Map<String, Object> sentArgs = parse(captor.getValue());
        assertEquals("sid-123", sentArgs.get("sessionId"));
        assertEquals("p-1", sentArgs.get("productId"));
        assertEquals(2, sentArgs.get("quantity"));
        assertEquals(INNER_JSON, result);
    }

    @Test
    void emptyToolInputBecomesArgsWithSessionId() throws Exception {
        CredentialInjectingToolCallback cb = callback(AUTH_SCHEMA);
        when(credentialStore.find("t1"))
                .thenReturn(Optional.of(new CredentialStore.Credential("sid-123", "13800001111")));
        when(delegate.call(anyString())).thenReturn(contentArray(INNER_JSON));

        cb.call("", context("t1"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(delegate).call(captor.capture());
        assertEquals("sid-123", parse(captor.getValue()).get("sessionId"));
    }

    @Test
    void openToolNeverTouchesCredentialStore() throws Exception {
        CredentialInjectingToolCallback cb = callback(OPEN_SCHEMA);
        when(delegate.call(anyString())).thenReturn(contentArray(INNER_JSON));

        cb.call("{}", context("t1"));

        verifyNoInteractions(credentialStore);
    }
}