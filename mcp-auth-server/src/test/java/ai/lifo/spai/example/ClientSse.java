package ai.lifo.spai.example;

import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author zhanglifeng
 * @since 2026-05-14
 */
public class ClientSse {

    public static void main(String[] args) {
        WebClient.Builder builder = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer yingzi-1")
                .baseUrl("http://localhost:20000");

        WebFluxSseClientTransport transport = new WebFluxSseClientTransport(builder, McpJsonMapper.getDefault());

        new SampleClient(transport).run();
    }
}
