package ai.lifo.agent.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

/**
 * @author zhanglifeng
 * @since 2026-01-15
 */
@Slf4j
public class WeatherTool implements BiFunction<String, ToolContext, String> {


    @Tool(description = "Get weather information for a city")
    @Override
    public String apply(@ToolParam(description = "City name") String city, ToolContext toolContext) {
        log.info("Getting weather for city: {}", city);

        RunnableConfig runnableConfig = (RunnableConfig) toolContext.getContext().get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);
        System.out.println(runnableConfig);
        runnableConfig.threadId().ifPresent(threadId -> System.out.println("Thread ID: " + threadId));

        // 模拟天气API调用
        return "城市：" + city + " 天气：多云转晴";
    }
}
