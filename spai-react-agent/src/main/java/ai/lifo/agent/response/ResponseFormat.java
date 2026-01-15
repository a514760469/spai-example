package ai.lifo.agent.response;

/**
 * @author zhanglifeng
 * @since 2026-01-15
 */
public class ResponseFormat {

    // 一个双关语响应（始终必需）
    private String punnyResponse;

    // 如果可用的话，关于天气的任何有趣信息
    private String weatherConditions;

    public String getPunnyResponse() {
        return punnyResponse;
    }

    public void setPunnyResponse(String punnyResponse) {
        this.punnyResponse = punnyResponse;
    }

    public String getWeatherConditions() {
        return weatherConditions;
    }

    public void setWeatherConditions(String weatherConditions) {
        this.weatherConditions = weatherConditions;
    }
}
