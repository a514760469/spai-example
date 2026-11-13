package ai.lifo.agent.graph.translate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

/**
 * 人工反馈条件边：读取 state 中的 humannextnode 决定下一跳。
 * 用户在 resume 时通过 graph.updateState() 写入该值。
 *
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class HumanFeedbackDispatcher implements EdgeAction {

    public static final String STATE_KEY = "human_nextnode";

    @Override
    public String apply(OverAllState state) throws Exception {
        String value = state.value(STATE_KEY, StateGraph.END);
        log.info("human feedback next node: {}", value);
        return value;
    }
}
