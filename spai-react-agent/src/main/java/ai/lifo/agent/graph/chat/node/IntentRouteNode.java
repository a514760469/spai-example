package ai.lifo.agent.graph.chat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 意图路由节点：最后一条用户消息含"翻译"等关键字时路由到翻译子图，
 * 并为子图补齐 query / expandernumber 输入；否则走普通聊天。
 *
 * @author zhanglifeng
 * @since 2026-11-13
 */
@Slf4j
public class IntentRouteNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "intent_route";

    /** 路由结果写入的 state key，由 IntentRouteDispatcher 读取 */
    public static final String ROUTE_KEY = "route";

    public static final String ROUTE_CHAT = "chat";

    public static final String ROUTE_TRANSLATE = "translate_flow";

    private static final int DEFAULT_EXPANDER_NUMBER = 3;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        //noinspection unchecked
        List<UserMessage> messages = (List<UserMessage>) state.value("messages").orElse(List.of());
        String text = messages.isEmpty() ? "" : messages.getLast().getText();
        String normalized = text.toLowerCase();

        if (normalized.contains("翻译") || normalized.contains("translate")) {
            log.info("intent routed to 【translate】, query={}", text);
            return Map.of(
                    ROUTE_KEY, ROUTE_TRANSLATE,
                    "query", text,
                    "expandernumber", DEFAULT_EXPANDER_NUMBER
            );
        }
        log.info("intent routed to 【chat】");
        return Map.of(ROUTE_KEY, ROUTE_CHAT);
    }
}