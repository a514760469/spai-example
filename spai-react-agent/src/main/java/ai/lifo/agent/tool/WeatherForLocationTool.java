package ai.lifo.agent.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

/**
 * 天气查询工具
 * @author zhanglifeng
 * @since 2026-01-15
 */
public class WeatherForLocationTool implements BiFunction<String, ToolContext, String> {


    @Override
    public String apply(@ToolParam(description = "The city name") String city, ToolContext toolContext) {
        return "It's always sunny in " + city + "!";
    }
}
