package ai.lifo.agent.graph.chat;

import ai.lifo.agent.config.UserPreferences;
import ai.lifo.agent.graph.TranslateCompiledGraph;
import ai.lifo.agent.graph.chat.node.ChatNode;
import ai.lifo.agent.graph.chat.node.IntentRouteDispatcher;
import ai.lifo.agent.graph.chat.node.IntentRouteNode;
import ai.lifo.agent.graph.chat.node.LoadUserPreferenceNode;
import ai.lifo.agent.graph.translate.ExpanderNode;
import ai.lifo.agent.graph.translate.ExpanderProcessNode;
import ai.lifo.agent.graph.translate.HumanFeedbackDispatcher;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-11-05
 */
public class ChatCompiledGraph {

    /**
     * 状态键策略工厂
     */
    public static final KeyStrategyFactory keyStrategyFactory = () -> {
        Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
        // ---- 父图原有 key ----
        keyStrategyMap.put("userId", new ReplaceStrategy());
        keyStrategyMap.put("messages", new AppendStrategy());
        keyStrategyMap.put(UserPreferences.KEY, new ReplaceStrategy());
        keyStrategyMap.put(ChatNode.OUTPUT_KEY, new ReplaceStrategy());

        // ---- 路由 key（IntentRouteDispatcher 读取）----
        keyStrategyMap.put(IntentRouteNode.ROUTE_KEY, new ReplaceStrategy());

        // ---- 翻译子图 key（与 子图 的策略保持一致）----
        keyStrategyMap.put("query", new ReplaceStrategy());
        keyStrategyMap.put("expandernumber", new ReplaceStrategy());
        keyStrategyMap.put(ExpanderNode.STREAM_OUTPUT_KEY, new ReplaceStrategy());
        keyStrategyMap.put(ExpanderProcessNode.OUTPUT_KEY, new ReplaceStrategy());
        keyStrategyMap.put(HumanFeedbackDispatcher.STATE_KEY, new ReplaceStrategy());
        keyStrategyMap.put("translatelanguage", new ReplaceStrategy());
        keyStrategyMap.put("translatecontent", new ReplaceStrategy());
        return keyStrategyMap;
    };

    public static CompiledGraph createGraph(ChatModel chatModel, BaseCheckpointSaver saver)
            throws GraphStateException {

        // 翻译图作为子图
        CompiledGraph transGraph = TranslateCompiledGraph.createGraph(chatModel, saver);

        String transGraphNode = "transGraphNode";

        // 配置 ChatClient
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

        // 构建图
        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                .addNode(LoadUserPreferenceNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(new LoadUserPreferenceNode()))
                .addNode(ChatNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(new ChatNode(chatClientBuilder)))
                .addNode(IntentRouteNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(new IntentRouteNode()))
                .addNode(transGraphNode, transGraph)
                .addConditionalEdges(IntentRouteNode.NODE_NAME, AsyncEdgeAction.edge_async(new IntentRouteDispatcher()),
                        Map.of(IntentRouteNode.ROUTE_CHAT, ChatNode.NODE_NAME,
                                IntentRouteNode.ROUTE_TRANSLATE, transGraphNode))
                .addEdge(StateGraph.START, LoadUserPreferenceNode.NODE_NAME)
                .addEdge(LoadUserPreferenceNode.NODE_NAME, IntentRouteNode.NODE_NAME)
                .addEdge(ChatNode.NODE_NAME, StateGraph.END)
                .addEdge(transGraphNode, StateGraph.END);

        // 配置检查点（短期内存）
        SaverConfig saverConfig = SaverConfig.builder()
                .register(saver)
                .build();

        // 编译图
        return stateGraph.compile(
                CompileConfig.builder()
                        .saverConfig(saverConfig)
                        .build()
        );
    }


}
