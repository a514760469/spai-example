package ai.lifo.spai.shopping.memory;

import ai.lifo.spai.shopping.creds.CredentialStore;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按需召回用户长期记忆：身份由当前 thread 的登录凭证决定，模型无法也无需传用户标识。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRecallTool {

    private static final int RECALL_TOP_K = 3;

    private final MemoryService memoryService;

    private final CredentialStore credentialStore;

    private final ObjectMapper objectMapper;

    @Tool(name = "recallUserMemory", description = """
            召回当前用户的跨会话长期记忆（偏好/画像事实，如预算习惯、品牌偏好、忌口过敏、送礼对象）。
            当用户需求可能与此前交流过的偏好相关时调用；返回空表示没有相关记忆，按正常流程继续。""")
    public String recallUserMemory(String query, ToolContext ctx) {
        RunnableConfig config = (RunnableConfig) ctx.getContext()
                .get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);
        String parentThread = ThreadIds.parent(config.threadId().orElse(null));

        var credential = parentThread == null ? java.util.Optional.<CredentialStore.Credential>empty()
                : credentialStore.find(parentThread);
        if (credential.isEmpty()) {
            return "{\"status\":\"no_login\",\"message\":\"用户尚未登录，无可用记忆\"}";
        }
        try {
            List<MemoryService.MemoryHit> hits = memoryService.recall(credential.get().phoneNumber(), query, RECALL_TOP_K);
            if (hits.isEmpty()) {
                return "{\"status\":\"empty\"}";
            }
            log.info("recallUserMemory query={}, hits={}", query, hits.size());
            return objectMapper.writeValueAsString(hits);
        }
        catch (Exception e) {
            log.error("recallUserMemory error", e);
            return "{\"status\":\"error\",\"message\":\"记忆检索暂不可用，按当前对话信息继续\"}";
        }
    }
}