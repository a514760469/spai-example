package ai.lifo.spai.example.resource;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-04-29
 */
@Configuration
public class ProviderConfiguration {

    /**
     * 添加一个时间工具
     *
     * @param timeService 时间服务
     * @return 时间工具
     */
    @Bean
    public ToolCallbackProvider timeTools(TimeService timeService) {
        return MethodToolCallbackProvider.builder().toolObjects(timeService).build();
    }

    /**
     * 添加一个股票工具
     *
     * @param stockService 股票服务
     * @return 股票工具
     */
    @Bean
    public ToolCallbackProvider stockTools(StockService stockService) {
        return MethodToolCallbackProvider.builder().toolObjects(stockService).build();
    }

    /**
     * 添加一个天气工具
     *
     * @param weatherService 天气服务
     * @return 天气工具
     */
    @Bean
    public ToolCallbackProvider weatherTools(OpenMeteoService weatherService) {
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }

}
