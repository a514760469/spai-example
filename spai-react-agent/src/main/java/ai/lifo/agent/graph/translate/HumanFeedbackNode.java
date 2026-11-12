package ai.lifo.agent.graph.translate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 人工反馈节点（中断锚点）。
 * 通过 InterruptableAction.interrupt 在执行前判断：State 中没有 humannextnode 时暂停，
 * 等待外部通过 updateState + resume 注入 humannextnode。
 * 节点本身不做任何业务处理。
 *
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class HumanFeedbackNode implements AsyncNodeActionWithConfig, InterruptableAction {

    public static final String NODE_NAME = "human_feedback";

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
        log.info("human_feedback node resumed, humannextnode={}",
                state.value(HumanFeedbackDispatcher.STATE_KEY).orElse("END"));
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        // 如果 state 里还没有 humannextnode → 中断等待用户输入
        if (state.value(HumanFeedbackDispatcher.STATE_KEY).isEmpty()) {
            return Optional.of(InterruptionMetadata.builder(nodeId, state)
                    .addMetadata("message", "请选择下一步：expander(重新生成) / translate(继续翻译) / __END__(结束)")
                    .addMetadata("node", nodeId)
                    .build());
        }
        // 已有反馈值，放行
        return Optional.empty();
    }
}