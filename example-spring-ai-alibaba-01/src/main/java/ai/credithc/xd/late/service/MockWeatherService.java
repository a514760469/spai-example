package ai.credithc.xd.late.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.function.Function;

/**
 * @author zhanglifeng
 * @since 2025-03-20
 */
public class MockWeatherService implements Function<MockWeatherService.Request, MockWeatherService.Response> {

    public enum Unit { C, F }

    @JsonClassDescription("Get the weather in location")
    public record Request(String location, Unit unit) {}

    public record Response(double temp, Unit unit) {}

    @Override
    public Response apply(Request request) {
        return new Response(30.0, Unit.C);
    }
}
