package ai.lifo.agent.graph;

import ai.lifo.agent.graph.translate.*;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 扩展 → 人工反馈 → 翻译 完整流程
 *
 * 流程：
 *   START → expander(流式) → expander_process(拆分) → human_feedback(中断)
 *         → [用户选择] → expander(重来) | translate(继续) | END(结束)
 *   translate → END
 *
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class HumanFeedbackCompiledGraph {

    public static CompiledGraph createGraph(ChatModel chatModel, BaseCheckpointSaver saver)
            throws GraphStateException {

        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

        // ---- 状态 key 策略 ----
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("query", new ReplaceStrategy());
            strategies.put("expandernumber", new ReplaceStrategy());
            strategies.put(ExpanderNode.STREAM_OUTPUT_KEY, new ReplaceStrategy());
            strategies.put(ExpanderProcessNode.OUTPUT_KEY, new ReplaceStrategy());
            strategies.put(HumanFeedbackDispatcher.STATE_KEY, new ReplaceStrategy());
            strategies.put("translatelanguage", new ReplaceStrategy());
            strategies.put("translatecontent", new ReplaceStrategy());
            return strategies;
        };

        // ---- 构建图 ----
        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode(ExpanderNode.NODE_NAME,
                        node_async(new ExpanderNode(chatClientBuilder)))
                .addNode(ExpanderProcessNode.NODE_NAME, node_async(new ExpanderProcessNode()))
                .addNode(HumanFeedbackNode.NODE_NAME, new HumanFeedbackNode())
                .addNode("translate",
                        node_async(new TranslateNode(chatClientBuilder)))

                // START → expander → expander_process → human_feedback
                .addEdge(START, ExpanderNode.NODE_NAME)
                .addEdge(ExpanderNode.NODE_NAME, ExpanderProcessNode.NODE_NAME)
                .addEdge(ExpanderProcessNode.NODE_NAME, HumanFeedbackNode.NODE_NAME)
                // human_feedback → 条件路由（由 HumanFeedbackDispatcher 决定）
                .addConditionalEdges(HumanFeedbackNode.NODE_NAME,
                        AsyncEdgeAction.edge_async(new HumanFeedbackDispatcher()),
                        Map.of(
                                ExpanderNode.NODE_NAME, ExpanderNode.NODE_NAME,  // 重来
                                "translate", "translate",                        // 继续翻译
                                END, END                                        // 直接结束
                        ))

                // translate → END
                .addEdge("translate", END);

        // ---- 编译：interruptBefore 在 human_feedback 前暂停 ----
        return graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build())
                // ---- 编译：不再需要 interruptBefore，中断由 InterruptableAction 控制 ----
//                .interruptBefore(HumanFeedbackNode.NODE_NAME)
                .build());
    }

    public static void main(String[] args) throws Exception {
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();

        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .defaultOptions(DashScopeChatOptions.builder().model("qwen3.8-27b").build())
                .dashScopeApi(dashScopeApi)
                .build();

        // 创建持久化器
        var saver = new FileSystemSaver.Builder().targetFolder(Path.of(".workspace/store")).build();

        CompiledGraph graph = createGraph(chatModel, saver);

        // ===== 第一次执行：跑到 human_feedback 前自动暂停 =====
        RunnableConfig config = RunnableConfig.builder()
                .threadId("demo-thread-1")
                .build();

        Map<String, Object> input = Map.of(
                "query", "Spring AI Alibaba 是什么？",
                "expandernumber", 3,
                "translatelanguage", "English"
        );

        graph.stream(input, config)
                .doOnNext(output -> log.info("节点输出: {}", output))
                .blockLast();

        // 此时可以查看 expander 生成的结果
        var snapshot = graph.getState(config);
        log.info("扩展结果: {}", snapshot.state().data().get("expandercontent"));

        // ===== 用户决策：注入 humannextnode，然后恢复 =====
        // 选择 "translate" 继续翻译；选 "expander" 重新生成；选 END 直接结束
        RunnableConfig updatedConfig = graph.updateState(
                config,
                Map.of(HumanFeedbackDispatcher.STATE_KEY, "translate"),
                null  // interruptBefore 模式下传 null
        );

        // ===== 第二次执行：从 human_feedback 恢复 =====
        graph.stream(null, updatedConfig)
                .doOnNext(output -> log.info("恢复后输出: {}", output))
                .blockLast();

        // 拿到翻译结果
        var finalState = graph.getState(config);
        log.info("翻译结果: {}", finalState.state().data().get("translatecontent"));

    }
}