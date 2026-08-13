package ai.lifo.spai.example;

import com.alibaba.cloud.ai.mcp.router.core.discovery.McpServiceDiscovery;
import com.alibaba.cloud.ai.mcp.router.core.discovery.McpServiceDiscoveryFactory;
import com.alibaba.cloud.ai.mcp.router.model.McpServerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;


/**
 * @author zhanglifeng
 * @since 2026-04-30
 */
@Slf4j
@SpringBootApplication(exclude = {McpServerAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class})
public class McpClientWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientWebApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(McpServiceDiscovery mcpServiceDiscovery, McpServiceDiscoveryFactory factory) {
        return args -> {
            log.info("=== MCP 多源服务发现演示 ===");

            // 显示已注册的服务发现类型
            log.info("已注册的服务发现类型: {}", factory.getRegisteredTypes());
            log.info("注册的服务发现实现数量: {}", factory.size());

            // 测试服务查找
            testServiceDiscovery(mcpServiceDiscovery, "weather-service");
            testServiceDiscovery(mcpServiceDiscovery, "dashscope-chat");
            testServiceDiscovery(mcpServiceDiscovery, "search-service");
            testServiceDiscovery(mcpServiceDiscovery, "non-existent-service");

            log.info("=== 演示完成 ===");
        };
    }

    private void testServiceDiscovery(McpServiceDiscovery discovery, String serviceName) {
        log.info("=== 测试服务发现: {} ===", serviceName);
        try {
            McpServerInfo serverInfo = discovery.getService(serviceName);
            if (serverInfo != null) {
                log.info("  ✓ 找到服务: {}", serverInfo.getName());
                log.info("    描述: {}", serverInfo.getDescription());
                log.info("    协议: {}", serverInfo.getProtocol());
                log.info("    版本: {}", serverInfo.getVersion());
                log.info("    端点: {}", serverInfo.getEndpoint());
                log.info("    标签: {}", serverInfo.getTags());
            }
            else {
                log.warn("  ✗ 未找到服务: {}", serviceName);
            }
        }
        catch (Exception e) {
            log.error("  ✗ 查找服务时发生错误: {}", serviceName, e);
        }
        log.info("");
    }

}
