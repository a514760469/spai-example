package ai.lifo.spai.shopping.controller;

import ai.lifo.spai.shopping.memory.MemoryExtractionService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback.FeedbackResult;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotEmpty;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天端点：类型化 SSE 帧 + HITL 中断恢复。
 * <pre>
 * POST /chat/stream  {threadId, message}   → delta / tool_call / tool_result / routing / interrupt / done
 * POST /chat/resume  {threadId, feedbacks} → 从 MysqlSaver checkpoint 恢复子图，继续推流
 * GET  /chat/threads                       → 历史会话列表（父线程，按最近活跃排序）
 * GET  /chat/history?threadId=             → 会话消息记录（用户 + 助手最终回复）
 * </pre>
 * 中断时子图 checkpoint 落在 {threadId}_subgraph_{agentName}，父图 checkpoint 的 nextNodeId
 * 指向子图节点（ResumableSubGraphAction），resume 带 HUMAN_FEEDBACK metadata 即可自动接力。
 * 待审批的 InterruptionMetadata 按 threadId 缓存在内存，服务重启后需重新发起对话。
 *
 * @author zhanglifeng
 * @since 2026-11-18
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int TOOL_RESULT_MAX_LENGTH = 4000;

    private final CompiledGraph shoppingGraph;

    private final ObjectMapper objectMapper;

    private final JdbcTemplate jdbcTemplate;

    private final MemoryExtractionService memoryExtractionService;

    private final MeterRegistry meterRegistry;

    private final Map<String, InterruptionMetadata> pendingInterrupts = new ConcurrentHashMap<>();

    public record ChatRequest(@NotEmpty(message = "threadId 不能为空") String threadId,
                              @NotEmpty(message = "message 不能为空") String message) {
    }

    public record ResumeRequest(@NotEmpty(message = "threadId 不能为空") String threadId,
                                @NonNull List<Decision> feedbacks) {
    }

    public record Decision(String id, String result, String arguments, String reason) {
    }

    public record ThreadSummary(String threadId, String title, long lastActive) {
    }

    public record HistoryMessage(String role, String text) {
    }

    /**
     * 历史会话列表：只列父线程（子图线程是内部实现），按最近活跃排序。
     * 标题取首条用户消息；GRAPH_THREAD 无创建时间，用最新 checkpoint 时间代替。
     */
    @GetMapping("/threads")
    public List<ThreadSummary> threads() {
        record Row(String threadName, long lastActive) {
        }
        List<Row> rows = jdbcTemplate.query("""
                SELECT t.thread_name, UNIX_TIMESTAMP(MAX(c.saved_at)) AS last_active
                FROM GRAPH_THREAD t
                LEFT JOIN GRAPH_CHECKPOINT c ON c.thread_id = t.thread_id
                WHERE t.is_released = FALSE AND t.thread_name NOT LIKE '%\\_subgraph\\_%'
                GROUP BY t.thread_name
                ORDER BY last_active DESC
                """, (rs, i) -> new Row(rs.getString("thread_name"), rs.getLong("last_active")));

        List<ThreadSummary> result = new ArrayList<>(rows.size());
        for (Row row : rows) {
            result.add(new ThreadSummary(row.threadName(), firstUserMessage(row.threadName()), row.lastActive()));
        }
        return result;
    }

    /**
     * 会话历史：父图最新 checkpoint 的 messages 即"用户输入 + 子 agent 最终回复"序列，
     * 与聊天 UI 展示粒度一致（工具中间过程在子图 checkpoint，不回看）。
     */
    @GetMapping("/history")
    public List<HistoryMessage> history(@RequestParam String threadId) {
        return shoppingGraph.stateOf(RunnableConfig.builder().threadId(threadId).build())
                .map(snapshot -> {
                    List<?> messages = snapshot.state().<List<?>>value("messages").orElse(List.of());
                    List<HistoryMessage> result = new ArrayList<>();
                    for (Object message : messages) {
                        if (message instanceof UserMessage user) {
                            result.add(new HistoryMessage("user", user.getText()));
                        }
                        else if (message instanceof AssistantMessage assistant
                                && StringUtils.hasText(assistant.getText())) {
                            result.add(new HistoryMessage("assistant", assistant.getText()));
                        }
                    }
                    return result;
                })
                .orElse(List.of());
    }

    private String firstUserMessage(String threadId) {
        try {
            List<?> messages = shoppingGraph.stateOf(RunnableConfig.builder().threadId(threadId).build())
                    .map(snapshot -> snapshot.state().<List<?>>value("messages").orElse(List.of()))
                    .orElse(List.of());
            for (Object message : messages) {
                if (message instanceof UserMessage user && StringUtils.hasText(user.getText())) {
                    String text = user.getText();
                    return text.length() <= 30 ? text : text.substring(0, 30) + "…";
                }
            }
        }
        catch (Exception e) {
            log.warn("读取会话标题失败, thread={}: {}", threadId, e.getMessage());
        }
        return "会话 " + threadId.substring(0, Math.min(8, threadId.length()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Validated @RequestBody ChatRequest request) {
        Map<String, Object> inputs = Map.of(
                "input", request.message(),
                "messages", List.of(new UserMessage(request.message())));
        RunnableConfig config = RunnableConfig.builder().threadId(request.threadId()).build();
        // 流正常结束后异步抽取长期记忆；不应答路径、不阻塞用户
        return toSse(shoppingGraph.stream(inputs, config), request.threadId())
                .doOnComplete(() -> memoryExtractionService.extractAsync(request.threadId()));
    }

    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> resume(@RequestBody ResumeRequest request) {
        InterruptionMetadata pending = pendingInterrupts.remove(request.threadId());
        if (pending == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.CONFLICT, "该会话没有待确认的操作"));
        }

        List<ToolFeedback> feedbacks = pending.toolFeedbacks().stream()
                .map(tf -> applyDecision(tf, request.feedbacks()))
                .toList();
        // builder(pending) 不拷贝 toolFeedbacks（源码里被注释掉了），必须显式回填
        InterruptionMetadata feedback = InterruptionMetadata.builder(pending)
                .toolFeedbacks(feedbacks)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(request.threadId())
                .addHumanFeedback(feedback)
                .build();
        return toSse(shoppingGraph.stream(Map.of(), config), request.threadId());
    }

    private ToolFeedback applyDecision(ToolFeedback tf, List<Decision> decisions) {
        Decision decision = decisions == null ? null : decisions.stream()
                .filter(d -> tf.getId().equals(d.id()))
                .findFirst()
                .orElse(null);
        ToolFeedback.Builder builder = ToolFeedback.builder(tf);
        if (decision == null || !StringUtils.hasText(decision.result())) {
            // 不给 result，validateFeedback 会再次中断，等待人工输入
            return builder.build();
        }
        FeedbackResult result;
        try {
            result = FeedbackResult.valueOf(decision.result().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 result: " + decision.result());
        }
        builder.result(result);
        meterRegistry.counter("shopping.hitl.events", "event", "decision",
                "tool", tf.getName(), "result", result.name()).increment();
        if (result == FeedbackResult.EDITED && StringUtils.hasText(decision.arguments())) {
            builder.arguments(decision.arguments());
        }
        if (result == FeedbackResult.REJECTED) {
            builder.description(StringUtils.hasText(decision.reason()) ? decision.reason() : "用户拒绝了该操作");
        }
        return builder.build();
    }

    private Flux<ServerSentEvent<String>> toSse(Flux<NodeOutput> graphStream, String threadId) {
        return graphStream
                .concatMap(output -> Flux.fromIterable(toFrames(output, threadId)))
                .map(this::sse)
                .concatWith(Flux.just(sse(Map.of("type", "done"))))
                .onErrorResume(e -> {
                    log.error("chat stream error, thread={}", threadId, e);
                    return Flux.just(sse(Map.of("type", "error", "message", String.valueOf(e.getMessage()))));
                });
    }

    private List<Map<String, Object>> toFrames(NodeOutput output, String threadId) {
        if (output instanceof InterruptionMetadata interruption) {
            pendingInterrupts.put(threadId, interruption);
            interruption.toolFeedbacks().forEach(tf -> meterRegistry
                    .counter("shopping.hitl.events", "event", "interrupted", "tool", tf.getName()).increment());
            List<Map<String, Object>> feedbacks = interruption.toolFeedbacks().stream()
                    .map(tf -> Map.<String, Object>of(
                            "id", tf.getId(),
                            "name", tf.getName(),
                            "arguments", tf.getArguments() == null ? "" : tf.getArguments(),
                            "description", tf.getDescription() == null ? "" : tf.getDescription()))
                    .toList();
            return List.of(frame("interrupt", output, Map.of("feedbacks", feedbacks)));
        }

        if ("intent_router".equals(output.node())) {
            String route = output.state() != null
                    ? output.state().<String>value("route").orElse("GUIDE")
                    : "GUIDE";
            return List.of(frame("routing", output, Map.of("route", route)));
        }

        if (output instanceof StreamingOutput<?> streaming) {
            OutputType outputType = streaming.getOutputType();
            if (outputType == OutputType.AGENT_MODEL_STREAMING) {
                String chunk = streaming.message().getText();
                if (!StringUtils.hasText(chunk) && streaming.message() instanceof AssistantMessage assistant) {
                    chunk = assistant.getText();
                }
                if (StringUtils.hasText(chunk)) {
                    return List.of(frame("delta", output, Map.of("text", chunk)));
                }
                return List.of();
            }
            if (outputType == OutputType.AGENT_MODEL_FINISHED
                    && streaming.message() instanceof AssistantMessage assistant
                    && assistant.hasToolCalls()) {
                List<Map<String, Object>> toolCalls = assistant.getToolCalls().stream()
                        .map(tc -> Map.<String, Object>of(
                                "id", tc.id(), "name", tc.name(), "arguments", tc.arguments()))
                        .toList();
                return List.of(frame("tool_call", output, Map.of("toolCalls", toolCalls)));
            }
            if (outputType == OutputType.AGENT_TOOL_FINISHED
                    && streaming.message() instanceof ToolResponseMessage toolResponse) {
                List<Map<String, Object>> responses = toolResponse.getResponses().stream()
                        .map(r -> Map.<String, Object>of(
                                "id", r.id(), "name", r.name(), "data", truncate(r.responseData())))
                        .toList();
                return List.of(frame("tool_result", output, Map.of("responses", responses)));
            }
            return List.of();
        }

        return List.of();
    }

    private Map<String, Object> frame(String type, NodeOutput output, Map<String, Object> extra) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        frame.put("node", output.node());
        frame.put("agent", output.agent());
        frame.putAll(extra);
        return frame;
    }

    private ServerSentEvent<String> sse(Map<String, Object> frame) {
        try {
            return ServerSentEvent.<String>builder().data(objectMapper.writeValueAsString(frame)).build();
        }
        catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .data("{\"type\":\"error\",\"message\":\"frame serialization failed\"}")
                    .build();
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= TOOL_RESULT_MAX_LENGTH
                ? text
                : text.substring(0, TOOL_RESULT_MAX_LENGTH) + "...(truncated)";
    }
}