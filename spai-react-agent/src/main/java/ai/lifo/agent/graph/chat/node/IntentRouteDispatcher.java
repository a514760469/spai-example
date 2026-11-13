package ai.lifo.agent.graph.chat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

/**
 * 意图条件边：读取 IntentRouteNode 写入的 route 值决定下一跳。
 *
 * @author zhanglifeng
 * @since 2026-11-13
 */
@Slf4j
public class IntentRouteDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) throws Exception {
        // default route to chat
        String route = state.value(IntentRouteNode.ROUTE_KEY, IntentRouteNode.ROUTE_CHAT);
        log.info("intent 路由为: {}", route);
        return route;
    }
}