package ai.lifo.spai.shopping.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图路由节点：用一次轻量 LLM 结构化输出调用把用户最新意图分类为
 * GUIDE / TRADE / AFTER_SALE，写入 state 的 route key，由条件边分发到领域子 Agent。
 * 路由失败时兜底到 GUIDE，保证对话不中断。
 *
 * @author zhanglifeng
 * @since 2026-11-18
 */
@Slf4j
public class IntentRouterNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "intent_router";
    public static final String ROUTE_KEY = "route";

    private static final int HISTORY_WINDOW = 6;

    private static final String ROUTER_SYSTEM_PROMPT = """
            你是电商导购系统的意图路由器。根据对话上下文判断用户最新一条消息的意图。
            只输出 GUIDE、TRADE、AFTER_SALE 三者之一，不要输出任何解释、标点或 JSON。

            分类定义：
            - GUIDE：商品搜索、推荐、对比、参数/价格/评价咨询、心愿单、配送时效咨询、闲聊问候
            - TRADE：加入购物车、结算、选择收货地址、支付、查询支付状态，以及承接上文的购买指令（如"就买这个""下单""来两件"）
            - AFTER_SALE：查询/取消订单、退货、物流包裹查询、收货 OTP、提交商品或配送评价、客服工单的创建/查询/回复

            规则：
            1. 以最新一条用户消息为主，结合上文消解指代（"这个""刚才那件"指向上一轮讨论的商品或订单）。
            2. 一条消息涉及多个意图时，选择用户当前最需要执行的动作。
            3. 无法判断时输出 GUIDE。
            """;

    private static final Pattern INTENT_PATTERN = Pattern.compile("GUIDE|TRADE|AFTER_SALE");

    private final ChatClient routerChatClient;

    public IntentRouterNode(ChatClient routerChatClient) {
        this.routerChatClient = routerChatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        String conversation = renderRecentMessages(state);
        ShoppingIntent intent = ShoppingIntent.GUIDE;
        try {
            String raw = routerChatClient.prompt()
                    .system(ROUTER_SYSTEM_PROMPT)
                    .user(conversation)
                    .call()
                    .content();
            intent = parseIntent(raw);
        }
        catch (Exception e) {
            log.warn("intent routing failed, fallback to GUIDE: {}", e.getMessage());
        }
        log.info("intent routed to 【{}】, threadId={}", intent, config.threadId().orElse("-"));
        return Map.of(ROUTE_KEY, intent.name());
    }

    /**
     * 部分模型对 enum JSON Schema 会返回 {"":"TRADE"} 之类的变体，
     * 直接对原始文本做枚举名匹配，兼容裸字符串与各种 JSON 包裹形式。
     */
    private ShoppingIntent parseIntent(String raw) {
        if (raw == null) {
            return ShoppingIntent.GUIDE;
        }
        Matcher matcher = INTENT_PATTERN.matcher(raw);
        if (matcher.find()) {
            return ShoppingIntent.valueOf(matcher.group());
        }
        log.warn("no intent found in router output [{}], fallback to GUIDE", raw);
        return ShoppingIntent.GUIDE;
    }

    private String renderRecentMessages(OverAllState state) {
        List<?> messages = state.<List<?>>value("messages").orElse(List.of());
        int from = Math.max(0, messages.size() - HISTORY_WINDOW);
        StringBuilder sb = new StringBuilder();
        for (Object item : messages.subList(from, messages.size())) {
            if (item instanceof Message message) {
                sb.append(message.getMessageType().getValue())
                        .append(": ")
                        .append(message.getText())
                        .append('\n');
            }
        }
        return sb.toString();
    }
}