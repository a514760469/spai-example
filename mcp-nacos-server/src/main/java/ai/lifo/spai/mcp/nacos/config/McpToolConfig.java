package ai.lifo.spai.mcp.nacos.config;

import ai.lifo.spai.mcp.nacos.service.NacosConfigToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Tool 注册配置，将 NacosConfigToolService 注册为 MCP Tool Provider。
 *
 * @author zhanglifeng
 * @since 2026-06-30
 */
@Slf4j
@Configuration
public class McpToolConfig {

    /**
     * 注册 Nacos 配置读取工具到 MCP Server。
     *
     * @param nacosConfigToolService Nacos 配置工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider nacosConfigTools(NacosConfigToolService nacosConfigToolService) {
        log.info("[McpToolConfig] 正在注册 Nacos 配置读取 MCP 工具: getConfig, listConfigs, searchConfig");
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(nacosConfigToolService).build();
        log.info("[McpToolConfig] Nacos MCP 工具注册完成, 工具数量={}", provider.getToolCallbacks().length);
        return provider;
    }
}
