package ai.lifo.spai.shopping.memory;

import ai.lifo.spai.shopping.creds.CredentialStore;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 每轮对话结束后的异步记忆抽取。
 * <p>
 * 输入是父图 checkpoint 里的本轮"用户消息 + 最终回复"，用 qwen-flash 抽持久事实（成本低、失败不影响主流程）。
 * 身份取自该 thread 的登录凭证；未登录会话直接跳过——没有身份就没有可归属的记忆。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@Service
public class MemoryExtractionService {

    private static final String EXTRACT_SYSTEM = """
            你是用户画像抽取器，从一轮电商导购对话中抽取"跨会话仍然成立"的持久事实。
            只抽取：预算档位与价格敏感度、品牌/类目偏好与排斥、口味忌口过敏、送礼对象与关系、常住地区、生活方式与使用场景。
            不抽取：本次咨询的具体商品与临时需求、已下单/已支付等交易决策、对助手的客套话、只在这一轮成立的临时结论。
            输出 JSON 数组，元素为 {"type":"PREFERENCE"|"PROFILE","content":"中文陈述句，不超过50字"}；没有可抽取内容输出 []。只输出 JSON，不要任何解释。
            """;

    private final ChatClient chatClient;

    private final CredentialStore credentialStore;

    private final MemoryService memoryService;

    private final CompiledGraph shoppingGraph;

    private final ObjectMapper objectMapper;

    private final String extractModel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "memory-extract");
        t.setDaemon(true);
        return t;
    });

    public MemoryExtractionService(ChatClient.Builder builder,
                                   CredentialStore credentialStore,
                                   MemoryService memoryService,
                                   CompiledGraph shoppingGraph,
                                   ObjectMapper objectMapper,
                                   @Value("${memory.extract-model:qwen-flash}") String extractModel) {
        this.chatClient = builder.build();
        this.credentialStore = credentialStore;
        this.memoryService = memoryService;
        this.shoppingGraph = shoppingGraph;
        this.objectMapper = objectMapper;
        this.extractModel = extractModel;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /** 主流程 doOnComplete 触发，任何异常只记日志 */
    public void extractAsync(String threadId) {
        executor.execute(() -> {
            try {
                extract(threadId);
            }
            catch (Exception e) {
                log.warn("记忆抽取失败, thread={}: {}", threadId, e.getMessage());
            }
        });
    }

    private void extract(String threadId) throws Exception {
        var credential = credentialStore.find(threadId);
        if (credential.isEmpty()) {
            return;
        }
        Turn turn = latestTurn(threadId);
        if (turn == null) {
            return;
        }

        String userMessage = "用户消息: " + turn.userText() + "\n助手回复: " + turn.assistantText();

        String reply = chatClient.prompt()
                .options(DashScopeChatOptions.builder().model(extractModel).multiModel(false).build())
                .system(EXTRACT_SYSTEM)
                .user(userMessage)
                .call()
                .content();

        log.info("记忆抽取结果: {}", reply);
        List<ExtractedFact> facts = parseFacts(reply);
        if (facts.isEmpty()) {
            return;
        }

        List<MemoryService.MemoryItem> items = facts.stream()
                .map(f -> new MemoryService.MemoryItem(normalizeType(f.type()), f.content()))
                .toList();

        int saved = memoryService.write(credential.get().phoneNumber(), threadId, items);
        log.info("记忆抽取完成, thread={}, extracted={}, saved={}", threadId, facts.size(), saved);
    }

    private record Turn(String userText, String assistantText) {
    }

    private Turn latestTurn(String threadId) {
        List<?> messages = shoppingGraph.stateOf(RunnableConfig.builder().threadId(threadId).build())
                .map(snapshot -> snapshot.state().<List<?>>value("messages").orElse(List.of()))
                .orElse(List.of());
        String userText = null;
        String assistantText = null;
        for (Object message : messages) {
            if (message instanceof UserMessage user && StringUtils.hasText(user.getText())) {
                userText = user.getText();
            }
            else if (message instanceof AssistantMessage assistant && StringUtils.hasText(assistant.getText())) {
                assistantText = assistant.getText();
            }
        }
        return userText == null ? null : new Turn(userText, assistantText == null ? "" : assistantText);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractedFact(String type, String content) {
    }

    private List<ExtractedFact> parseFacts(String reply) {
        if (!StringUtils.hasText(reply)) {
            return List.of();
        }
        String json = reply.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            ExtractedFact[] facts = objectMapper.readValue(json.substring(start, end + 1), ExtractedFact[].class);
            return Arrays.stream(facts)
                    .filter(f -> StringUtils.hasText(f.content()))
                    .toList();
        }
        catch (Exception e) {
            log.warn("记忆抽取结果解析失败: {}", json);
            return List.of();
        }
    }

    private String normalizeType(String type) {
        return "PREFERENCE".equalsIgnoreCase(type) ? "PREFERENCE" : "PROFILE";
    }
}