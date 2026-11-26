package ai.lifo.spai.shopping.controller;

import com.alibaba.cloud.ai.agent.studio.dto.GraphRunRequest;
import com.alibaba.cloud.ai.agent.studio.dto.GraphRunResponse;
import com.alibaba.cloud.ai.agent.studio.dto.messages.MessageDTO;
import com.alibaba.cloud.ai.agent.studio.dto.messages.ToolRequestConfirmMessageDTO;
import com.alibaba.cloud.ai.agent.studio.loader.GraphLoader;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling graph execution endpoints. Registered only when a {@link GraphLoader}
 * bean exists. Uses independent logic from Agent execution.
 *
 * @see com.alibaba.cloud.ai.agent.studio.controller.GraphExecutionController
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Slf4j
@RestController("myGraphExecutionController")
public class GraphExecutionController {

    private final ObjectMapper mapper = new ObjectMapper();

    private final GraphLoader graphLoader;

    private final Store store;

    @Autowired
    public GraphExecutionController(GraphLoader graphLoader, Store store) {
        this.graphLoader = graphLoader;
        this.store = store;
    }

    /**
     * Executes a graph run and streams the resulting events using Server-Sent Events (SSE).
     *
     * @param request The GraphRunRequest containing run details.
     * @return A Flux that streams events to the client in standard SSE format.
     */
    @PostMapping(value = "/graph_run_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> graphRunSse(@RequestBody GraphRunRequest request) {
        if (request.graphName == null || request.graphName.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "graphName cannot be null or empty"));
        }
        if (request.threadId == null || request.threadId.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "threadId cannot be null or empty"));
        }

        try {
            CompiledGraph graph = graphLoader.loadGraph(request.graphName);

            Map<String, Object> inputs;
            if (request.inputs != null && !request.inputs.isEmpty()) {
                inputs = new HashMap<>(request.inputs);
            }
            else {
                String content = (request.newMessage != null && request.newMessage.getContent() != null)
                        ? request.newMessage.getContent() : "";
                inputs = Map.of(
                        "query", content,
                        "input", content,
                        "messages", List.of(new UserMessage(content)),
                        "userId", request.userId
                );
            }

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(request.threadId)
                    .store(store)
                    .addMetadata("user_id", request.userId != null ? request.userId : "user-001")
                    .build();

            Flux<NodeOutput> graphStream = graph.stream(inputs, runnableConfig);

            return executeGraph(graphStream);
        }
        catch (Exception e) {
            log.error("Error during graph run for thread {}", request.threadId, e);
            return Flux.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Graph run failed", e));
        }
    }

    /**
     * 人工反馈恢复端点：注入用户决策后从中断点继续执行。
     *
     * 请求体示例：
     * {
     *   "graphName": "humanFeedbackGraph",
     *   "threadId": "demo-thread-1",
     *   "userId": "user-001",
     *   "inputs": { "human_nextnode": "translate" }
     * }
     */
    @PostMapping(value = "/graph_resume_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> graphResumeSse(@RequestBody GraphRunRequest request) {
        String graphName = request.getGraphName();
        String threadId = request.getThreadId();
        String userId = request.getUserId();
        if (graphName == null || graphName.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "graphName cannot be null or empty"));
        }
        if (threadId == null || threadId.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "threadId cannot be null or empty"));
        }
        Map<String, Object> inputs = request.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return Flux.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "inputs cannot be null or empty"));
        }
        try {
            CompiledGraph graph = graphLoader.loadGraph(graphName);
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .store(store)
                    .threadId(threadId)
                    .addMetadata("user_id", userId)
                    .build();
            // 反馈只经 values 注入 State；asNode 是"模拟刚执行完的节点"语义，
            // 传用户选择会让框架在当前图查它的出边（子图场景下必然不存在），必须传 null
            RunnableConfig updatedConfig = graph.updateState(runnableConfig, inputs, null);

            // 从中断点恢复执行（input 为 null，使用已有 State）
            Flux<NodeOutput> graphStream = graph.stream(null, updatedConfig);
            return executeGraph(graphStream);
        }
        catch (Exception e) {
            log.error("Error during graph resume for thread {}", threadId, e);
            return Flux.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Graph resume failed", e));
        }
    }

    private Flux<ServerSentEvent<String>> executeGraph(Flux<NodeOutput> graphStream) {
        return graphStream
                .filter(nodeOutput -> !(nodeOutput instanceof StreamingOutput<?> so
                        && so.getOutputType() == OutputType.AGENT_MODEL_FINISHED))
                .map(nodeOutput -> {
                    String node = nodeOutput.node();
                    String agentName = nodeOutput.agent();
                    Usage tokenUsage = nodeOutput.tokenUsage();
                    Map<String, Object> stateData = nodeOutput.state() != null
                            ? new LinkedHashMap<>(nodeOutput.state().data()) : null;

                    GraphRunResponse graphResponse;
                    if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                        Message message = streamingOutput.message();
                        if (message == null) {
                            // message 为空时回退到原始数据（ChatResponse / String），不再使用已废弃的 chunk()
                            Object originData = streamingOutput.getOriginData();
                            String text = originData instanceof ChatResponse chatResponse
                                    ? chatResponse.getResult().getOutput().getText()
                                    : originData != null ? String.valueOf(originData) : "";
                            graphResponse = new GraphRunResponse(node, agentName, (MessageDTO) null, tokenUsage, text, stateData);
                        }
                        else if (message instanceof AssistantMessage assistantMessage) {
                            if (assistantMessage.hasToolCalls()) {
                                graphResponse = new GraphRunResponse(node, agentName, assistantMessage, tokenUsage, "", stateData);
                            }
                            else {
                                graphResponse = new GraphRunResponse(node, agentName, assistantMessage, tokenUsage,
                                        assistantMessage.getText(), stateData);
                            }
                        }
                        else {
                            graphResponse = new GraphRunResponse(node, agentName, message, tokenUsage, "", stateData);
                        }
                    }
                    else if (nodeOutput instanceof InterruptionMetadata interruptionMetadata) {
                        ToolRequestConfirmMessageDTO toolRequestMessage =
                                MessageDTO.MessageDTOFactory.fromInterruptionMetadata(interruptionMetadata);
                        graphResponse = new GraphRunResponse(node, agentName, toolRequestMessage, tokenUsage, "", stateData);
                    }
                    else {
                        graphResponse = new GraphRunResponse(node, agentName, (MessageDTO) null, tokenUsage, "", stateData);
                    }

                    try {
                        String jsonData = mapper.writeValueAsString(graphResponse);
                        return ServerSentEvent.<String>builder().data(jsonData).build();
                    }
                    catch (Exception e) {
                        log.error("Failed to serialize GraphRunResponse to JSON", e);
                        return ServerSentEvent.<String>builder()
                                .data("{\"error\":\"Failed to serialize response\"}")
                                .build();
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error occurred during graph stream execution", error);
                    String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error occurred";
                    String errorType = error.getClass().getSimpleName();
                    String errorJson = String.format(
                            "{\"error\":true,\"errorType\":\"%s\",\"errorMessage\":\"%s\"}",
                            errorType.replace("\"", "\\\""),
                            errorMessage.replace("\"", "\\\"").replace("\n", "\\n"));
                    return Flux.just(ServerSentEvent.<String>builder().event("error").data(errorJson).build());
                });
    }
}
