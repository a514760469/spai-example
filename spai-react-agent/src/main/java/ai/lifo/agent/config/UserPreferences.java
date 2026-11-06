package ai.lifo.agent.config;

import lombok.Getter;

import java.util.List;

/**
 * 用户偏好类
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Getter
public class UserPreferences {
    public static final String KEY = UserPreferences.class.getSimpleName();
    public static final UserPreferences DEFAULT = new UserPreferences("专业", "zh", List.of(), "default", "Asia/Shanghai");

    private final String communicationStyle;
    private final String language;
    private final List<String> interests;
    private final String theme;
    private final String timezone;

    public UserPreferences(String style, String lang, List<String> interests) {
        this.communicationStyle = style;
        this.language = lang;
        this.interests = interests;
        this.theme = "default";
        this.timezone = "Asia/Shanghai";
    }

    public UserPreferences(String style, String lang, List<String> interests, String theme, String timezone) {
        this.communicationStyle = style;
        this.language = lang;
        this.interests = interests;
        this.theme = theme;
        this.timezone = timezone;
    }

    public String preferences() {
        return "communicationStyle: " + communicationStyle + ", language: " + language + ", interests: " + interests + ", theme: " + theme + ", timezone: " + timezone;
    }

}
