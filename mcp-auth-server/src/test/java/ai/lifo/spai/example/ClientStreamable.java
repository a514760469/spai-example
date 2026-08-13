package ai.lifo.spai.example;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author zhanglifeng
 * @since 2026-05-14
 */
public class ClientStreamable {

    public static void main(String[] args) {

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:20000")
                .httpRequestCustomizer((builder, method, endpoint, body, context) -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer yingzi-1"))
                .build();

        new SampleClient(transport).run();
    }
}
