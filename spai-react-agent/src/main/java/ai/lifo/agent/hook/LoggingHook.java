package ai.lifo.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author zhanglifeng
 * @since 2026-10-16
 */
@Slf4j
public class LoggingHook extends ModelHook {

    @Override
    public String getName() {
        return "LoggingHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // 在模型调用前记录
        List<?> messages = (List<?>) state.value("messages").orElse(List.of());
        log.info("调用前state {}", JSON.toJSONString(state));
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        // 在模型调用后记录
        log.info("调用后state {}", JSON.toJSONString(state));
        return CompletableFuture.completedFuture(Map.of());
    }

    /**
     * Spring AI Alibaba 支持以下 Hook 位置：
     * BEFORE_AGENT - Agent 开始之前
     * AFTER_AGENT - Agent 完成之后
     * BEFORE_MODEL - 模型调用之前
     * AFTER_MODEL - 模型调用之后
     */
    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
    }
}
