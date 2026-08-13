package ai.lifo.spai.example.resource;

import ai.lifo.spai.example.filter.UserInfoHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author zhanglifeng
 * @since 2026-04-29
 */
@Slf4j
@Service
public class TimeService {

    @Tool(description = "Get the time of a specified city.")
    public String getCityTimeMethod(@ToolParam(description = "Time zone id, such as Asia/Shanghai") String timeZoneId) {

        String userInfo = UserInfoHolder.getUserInfo();
        log.info("The current time zone is {} and the userInfo is {}", timeZoneId, userInfo);
        return String.format("The current time zone is %s and the current time is " + "%s", timeZoneId,
                getTimeByZoneId(timeZoneId));
    }

    private String getTimeByZoneId(String zoneId) {
        // Get the time zone using ZoneId
        ZoneId zid = ZoneId.of(zoneId);

        // Get the current time in this time zone
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zid);

        // Defining a formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

        // Format ZonedDateTime as a string
        return zonedDateTime.format(formatter);
    }
}
