package ai.lifo.spai.example;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.util.Map;

import static io.modelcontextprotocol.spec.McpSchema.*;

/**
 * @author zhanglifeng
 * @since 2026-05-14
 */
public class SampleClient {

    private final McpClientTransport transport;

    public SampleClient(McpClientTransport transport) {
        this.transport = transport;
    }

    public void run() {
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();
            client.ping();

            ListToolsResult toolsList = client.listTools();
            System.out.println("可用工具 = " + toolsList);
            // 北京天气预报
            CallToolResult callToolResult = client.callTool(new CallToolRequest("getWeatherForecastByLocation", Map.of("latitude", "39.9042", "longitude", "116.4074")));
            System.out.println("北京天气预报 = " + callToolResult);

            // 北京空气质量
            CallToolResult airQualityResult = client.callTool(new CallToolRequest("getAirQuality", Map.of("latitude", "39.9042", "longitude", "116.4074")));
            System.out.println("北京空气质量 = " + airQualityResult);

            client.closeGracefully();
        }

    }
}
