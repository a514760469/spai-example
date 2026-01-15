package ai.lifo.agent.tool;

import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

/**
 * @author zhanglifeng
 * @since 2026-01-15
 */
public class WeatherTool implements BiFunction<String, ToolContext, String> {


    @Override
    public String apply(String city, ToolContext toolContext) {
        return "It's always sunny in " + city + "!";
    }
}
