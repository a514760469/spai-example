package ai.credithc.xd.late.config;

import ai.credithc.xd.late.service.MockWeatherService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

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
