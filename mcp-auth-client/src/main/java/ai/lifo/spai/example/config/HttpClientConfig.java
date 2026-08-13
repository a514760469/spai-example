package ai.lifo.spai.example.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-04-29
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public McpSyncHttpClientRequestCustomizer mcpSyncHttpClientRequestCustomizer() {
        Map<String, String> headers = new HashMap<>();
        headers.put("token-1", "yingzi-1");
        headers.put("Authorization", "Bearer yingzi-1");

        return new HeaderSyncHttpRequestCustomizer(headers);
    }

    public static class HeaderSyncHttpRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

        private final Map<String, String> headers;

        public HeaderSyncHttpRequestCustomizer(Map<String, String> headers) {
            this.headers = headers;
        }

        @Override
        public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
            headers.forEach(builder::header);
        }
    }
}
