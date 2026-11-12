package ai.lifo.agent.graph;

import ai.lifo.agent.graph.email.*;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.JacksonStateSerializer;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * @author zhanglifeng
 * @since 2026-11-03
 */
@Slf4j
public class EmailCompiledGraph {


    public static CompiledGraph createEmailAgentGraph(ChatModel chatModel, BaseCheckpointSaver saver)
            throws GraphStateException {

        // 配置 ChatClient
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);


        // 创建节点
        var readEmail = node_async(new ReadEmailNode());
        var classifyIntent = node_async(new ClassifyIntentNode(chatClientBuilder));
        var searchDocumentation = node_async(new SearchDocumentationNode());
        var bugTracking = node_async(new BugTrackingNode());
        var draftResponse = node_async(new DraftResponseNode(chatClientBuilder));
        var humanReview = node_async(new HumanReviewNode());
        var sendReply = node_async(new SendReplyNode());


        // 创建图
        StateGraph graph = new StateGraph(EmailClassification.createKeyStrategyFactory())
                .addNode("read_email", readEmail)
                .addNode("classify_intent", classifyIntent)
                .addNode("search_documentation", searchDocumentation)
                .addNode("bug_tracking", bugTracking)
                .addNode("draft_response", draftResponse)
                .addNode("human_review", humanReview)
                .addNode("send_reply", sendReply);

        // 添加基本边
        graph.addEdge(StateGraph.START, "read_email");
        graph.addEdge("read_email", "classify_intent");
        graph.addEdge("send_reply", StateGraph.END);
        // 添加条件边（基于节点返回的 next_node）
        graph.addConditionalEdges("classify_intent", AsyncEdgeAction.edge_async(state -> {
            return (String) state.value("next_node").orElse("draft_response");
        }), Map.of(
                "search_documentation", "search_documentation",
                "bug_tracking", "bug_tracking",
                "human_review", "human_review",
                "draft_response", "draft_response"
        ));
        graph.addConditionalEdges("draft_response", AsyncEdgeAction.edge_async(state -> {
            return (String) state.value("next_node").orElse("send_reply");
        }), Map.of(
                "human_review", "human_review",
                "send_reply", "send_reply"
        ));
        graph.addConditionalEdges("human_review", AsyncEdgeAction.edge_async(state -> {
            return (String) state.value("next_node").orElse("send_reply");
        }), Map.of(
                "send_reply", "send_reply"
        ));
        graph.addEdge("search_documentation", "draft_response");
        graph.addEdge("bug_tracking", "draft_response");

        // 序列化器定制化修改
        StateSerializer stateSerializer = graph.getStateSerializer();
        if (stateSerializer instanceof JacksonStateSerializer jacksonSerializer) {
            ObjectMapper objectMapper = jacksonSerializer.objectMapper();
            // 1. 配置序列化选项
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        var compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(saver)
                        .build())
                .interruptBefore("human_review")  // 在人工审核前中断
                .build();

        return graph.compile(compileConfig);
    }


    // ==================== 测试方法 ====================
    /**
     * 测试紧急账单问题
     */
    public static void testBillingIssue(CompiledGraph app) throws Exception {
        log.info("=== 测试紧急账单问题 ===");

        // 测试紧急账单问题
        Map<String, Object> initialState = Map.of(
                "email_content", "我的订阅被收费两次了！这很紧急！",
                "sender_email", "customer@example.com",
                "email_id", "email_123",
                "messages", new ArrayList<String>()
        );

        // 使用 thread_id 运行以实现持久化
        var config = RunnableConfig.builder()
                .threadId("customer_123")
                .build();

        // 使用 stream 执行，直到中断点（human_review）
        // 图将在 human_review 处暂停（因为配置了 interruptBefore）
        Flux<NodeOutput> stream = app.stream(initialState, config);
        stream.doOnNext(output -> log.info("节点输出: {}", JSONObject.toJSONString(output, true)))
                .doOnError(error -> log.error("执行错误: {}", error.getMessage()))
                .doOnComplete(() -> log.info("流完成"))
                .blockLast();

        // 获取当前状态，检查是否有草稿回复
        var currentState = app.getState(config);
        Map<String, Object> stateData = currentState.state().data();
        String draftResponse = (String) stateData.get("draft_response");
        if (draftResponse != null) {
            log.info("Draft ready for review: {}...", draftResponse.length() > 100 ? draftResponse.substring(0, 100) : draftResponse);
        }

        // 准备好后，提供人工输入以恢复
        // 使用 updateState 更新状态（interruptBefore 模式下，传入 null 作为节点 ID）
        var updatedConfig = app.updateState(config, Map.of(
                "approved", true,
                "edited_response", "我们对重复收费深表歉意。我已经立即启动了退款..."
        ), null);

        // 继续执行（input 为 null，使用之前的状态）
        app.stream(null, updatedConfig)
                .doOnNext(output -> log.info("节点输出: {}", JSONObject.toJSONString(output)))
                .doOnError(error -> log.error("执行错误: {}", error.getMessage()))
                .doOnComplete(() -> log.info("流完成"))
                .blockLast();

        // 获取最终状态
        var finalState = app.getState(config);
        String status = (String) finalState.state().data().get("status");
        log.info("Email sent successfully! Status: {}", status);
    }

    /**
     * 测试简单问题
     */
    public static void testSimpleQuestion(CompiledGraph app) {
        log.info("=== 测试简单问题 ===");

        Map<String, Object> initialState = Map.of(
                "email_content", "如何重置我的密码？",
                "sender_email", "user@example.com",
                "email_id", "email_456",
                "messages", new ArrayList<String>()
        );

        var config = RunnableConfig.builder()
                .threadId("user_456")
                .build();

        // invoke 返回 Optional<OverAllState>，需要使用 orElseThrow() 获取结果
        var result = app.invoke(initialState, config).orElseThrow();
        String status = (String) result.data().get("status");
        log.info("Simple question processed. Status: {}", status);
    }

    /**
     * 主方法
     */
    public static void main(String[] args) throws Exception {
        log.info("========================================");
        log.info("Graph 工作流编排快速入门示例");
        log.info("========================================\n");

        // 注意：实际使用时需要提供 ChatModel 实例
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();

        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .build();

        // 创建持久化器
        var saver = new FileSystemSaver.Builder().targetFolder(Path.of(".workspace/store")).build();
//        var memory = new MemorySaver();
        CompiledGraph app = createEmailAgentGraph(chatModel, saver);

        testBillingIssue(app);
//		testSimpleQuestion(app);
    }
}
