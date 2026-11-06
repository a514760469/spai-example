package ai.lifo.agent.graph.chat;

import ai.lifo.agent.config.UserPreferences;
import ai.lifo.agent.graph.chat.node.ChatNode;
import ai.lifo.agent.graph.chat.node.LoadUserPreferenceNode;
import com.alibaba.cloud.ai.graph.*;
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

    public static CompiledGraph createGraph(ChatModel chatModel, BaseCheckpointSaver saver)
            throws GraphStateException {

        // 配置 ChatClient
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);


        // 创建图
        // 定义状态
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put("userId", new ReplaceStrategy());
            keyStrategyMap.put("messages", new AppendStrategy());
            keyStrategyMap.put(UserPreferences.KEY, new ReplaceStrategy());
            keyStrategyMap.put(ChatNode.OUTPUT_KEY, new ReplaceStrategy());
            return keyStrategyMap;
        };

        // 构建图
        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                .addNode(LoadUserPreferenceNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(new LoadUserPreferenceNode()))
                .addNode(ChatNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(new ChatNode(chatClientBuilder)))
                .addEdge(StateGraph.START, LoadUserPreferenceNode.NODE_NAME)
                .addEdge(LoadUserPreferenceNode.NODE_NAME, ChatNode.NODE_NAME)
                .addEdge(ChatNode.NODE_NAME, StateGraph.END);

        // 配置检查点（短期内存）
        SaverConfig saverConfig = SaverConfig.builder()
                .register(saver)
                .build();

        // 编译图
        CompiledGraph graph = stateGraph.compile(
                CompileConfig.builder()
                        .saverConfig(saverConfig)
                        .build()
        );
        return graph;


//        var compileConfig = CompileConfig.builder()
//                .saverConfig(SaverConfig.builder()
//                        .register(saver)
//                        .build())
//                .interruptBefore("human_review")  // 在人工审核前中断
//                .build();
//
//        return graph.compile(compileConfig);
    }


}
