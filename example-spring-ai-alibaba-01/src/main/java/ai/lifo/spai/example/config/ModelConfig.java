package ai.lifo.spai.example.config;

import ai.lifo.spai.example.service.MockWeatherService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * @author zhanglifeng
 * @since 2025-03-20
 */
@Configuration
public class ModelConfig {

    @Bean
//    @Description("Get the weather in location") // function description
    public Function<MockWeatherService.Request, MockWeatherService.Response> currentWeather() {
        return new MockWeatherService();
    }



}
