package ai.lifo.agent.graph;

import ai.lifo.agent.graph.translate.HumanFeedbackDispatcher;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 HumanFeedbackCompiledGraph 包装成 Agent，
 * 使其能被 studio 前端的 /run_sse + /resume_sse 协议驱动。
 *
 * 协议桥接：
 *   - 首次执行：Agent.stream(message, config) → graph.stream(inputs, config)
 *   - 恢复执行：Agent.stream("", config) → 从 config 提取 humanFeedback
 *                → APPROVED 映射为 "translate"，REJECTED 映射为 "expander"
 *                → graph.updateState() + graph.stream(null, updatedConfig)
 *
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class HumanFeedbackAgent extends Agent {

    private final CompiledGraph graph;
    private final String agentName;

    public HumanFeedbackAgent(CompiledGraph graph, String agentName) {
        this.graph = graph;
        this.agentName = agentName;
    }

    @Override
    public String name() {
        return agentName;
    }

    @Override
    public String description() {
        return "扩展查询 → 人工反馈 → 翻译 工作流（支持中断/恢复）";
    }

    @Override
    public CompiledGraph getAndCompileGraph() {
        return graph;
    }

    /**
     * 首次执行：用户发消息触发 graph
     */
    public Flux<NodeOutput> stream(Message message, RunnableConfig config) throws GraphRunnerException {
        String query = message instanceof UserMessage um ? um.getText() : message.getText();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", query);
        inputs.put("expandernumber", 3);
        inputs.put("translatelanguage", "English");

        return graph.stream(inputs, config);
    }

    @Override
    public Flux<NodeOutput> stream(List<Message> messages, RunnableConfig config) throws GraphRunnerException {
        return stream(messages.getLast(), config);
    }

    @Override
    protected StateGraph initGraph() throws GraphStateException {
        return null;
    }

    @Override
    public Flux<NodeOutput> stream(String input, RunnableConfig config) throws GraphRunnerException {
        if (input == null || input.isEmpty()) {
            // ---- 这是 resume 调用 ----
            return resumeFromFeedback(config);
        }
        return stream(new UserMessage(input), config);
    }

    @Override
    public Flux<NodeOutput> stream(String input) throws GraphRunnerException {
        return stream(input, RunnableConfig.builder().threadId("default").build());
    }

    /**
     * 核心桥接逻辑：从 RunnableConfig 中提取 studio 前端发来的 humanFeedback，
     * 映射成 graph 的 humannextnode，然后 updateState + resume。
     */
    private Flux<NodeOutput> resumeFromFeedback(RunnableConfig config) {
        try {
            // 1. 从 config 中提取 studio 前端传来的 InterruptionMetadata（human feedback）
            InterruptionMetadata interruptionMetadata =
                    config.metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, new TypeRef<InterruptionMetadata>() {})
                            .orElseThrow();

            // 2. 映射 feedback → humannextnode
            String nextNode = mapFeedbackToNextNode(interruptionMetadata);
            log.info("Human feedback received, mapping to nextNode={}", nextNode);

            // 3. 更新 graph state（注入用户决策）
            RunnableConfig updatedConfig = graph.updateState(
                    config,
                    Map.of(HumanFeedbackDispatcher.STATE_KEY, nextNode),
                    nextNode  // interruptBefore 模式传 null；InterruptableAction 模式传 nodeId
            );

            // 4. 恢复执行
            return graph.stream(null, updatedConfig);
        }
        catch (Exception e) {
            log.error("Failed to resume graph from human feedback", e);
            return Flux.error(e);
        }
    }

    /**
     * 把 studio 的二元 feedback (APPROVED/REJECTED) 映射成 graph 的路由值。
     *
     * APPROVED → "translate"（满意，继续翻译）
     * REJECTED → "expander"（不满意，重新生成）
     */
    private String mapFeedbackToNextNode(InterruptionMetadata humanFeedback) {
        if (humanFeedback == null) {
            return "translate"; // 默认继续
        }

        List<InterruptionMetadata.ToolFeedback> toolFeedbacks = humanFeedback.toolFeedbacks();
        if (toolFeedbacks == null || toolFeedbacks.isEmpty()) {
            return "translate";
        }

        // 取第一个 feedback 的 result
        InterruptionMetadata.ToolFeedback first = toolFeedbacks.getFirst();
        return switch (first.getResult()) {
            case APPROVED -> "translate";
            case REJECTED -> "expander";
            default -> "translate";
        };
    }
}