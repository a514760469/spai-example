package ai.lifo.spai.example.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-04-29
 */
@Slf4j
@Component
public class McpServerFilter implements WebFilter {

    private static final String TOKEN_HEADER = "Authorization";

    private static final String TOKEN_PREFIX = "Bearer ";

    private static final String TOKEN_VALUE = "yingzi-1";

    private static final Map<String, String> USER_INFO_MAP = Map.of(TOKEN_VALUE, "Fake_UserInfo");

    private static final boolean NO_VALIDATE = true;

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        for (String headerName : headers.keySet()) {
            log.info("headerName: {}, headerValue: {}", headerName, headers.getFirst(headerName));
        }

        String authToken = headers.getFirst(TOKEN_HEADER);
        log.info("preHandle: 请求的URL: {}", exchange.getRequest().getURI());
        log.info("preHandle: 请求的TOKEN: {}", authToken);
        if (NO_VALIDATE) {
            return chain.filter(exchange);
        }
        // 检查 token
        if (authToken != null && authToken.startsWith(TOKEN_PREFIX)) {
            String token = authToken.substring(TOKEN_PREFIX.length());
            if (TOKEN_VALUE.equals(token)) {
                log.info("preHandle: 验证通过");
                UserInfoHolder.setUserInfo(USER_INFO_MAP.get(authToken));
                // token验证通过，继续处理请求
                return chain.filter(exchange);
            }
        }
        // token验证失败，返回401未授权错误
        log.warn("Token验证失败: 请求的URL: {}, 提供的TOKEN: {}", exchange.getRequest().getURI(), authToken);
        log.warn("要求的token为：{}", TOKEN_VALUE);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
