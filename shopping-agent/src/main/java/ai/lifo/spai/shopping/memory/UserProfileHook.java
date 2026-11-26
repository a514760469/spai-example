package ai.lifo.spai.shopping.memory;

import ai.lifo.spai.shopping.creds.CredentialStore;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 常驻画像注入：beforeModel 时把用户长期记忆摘要前置为 SystemMessage。
 * 注入以标记幂等——messages 中已存在标记则本轮 no-op，避免 ReAct 各模型轮次与后续对话重复堆积。
 * thread 未登录或无记忆时跳过。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
@HookPositions({HookPosition.BEFORE_MODEL})
public class UserProfileHook extends MessagesModelHook {

    private static final String MARKER = "[用户记忆]";

    private final MemoryService memoryService;

    private final CredentialStore credentialStore;

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        boolean alreadyInjected = previousMessages.stream()
                .anyMatch(m -> {
                    if (!(m instanceof SystemMessage sm)) {
                        return false;
                    }
                    return sm.getText().startsWith(MARKER);
                });
        if (alreadyInjected) {
            return new AgentCommand(null);
        }
        String parentThread = ThreadIds.parent(config.threadId().orElse(null));
        if (parentThread == null) {
            return new AgentCommand(null);
        }
        var credential = credentialStore.find(parentThread);
        if (credential.isEmpty()) {
            return new AgentCommand(null);
        }
        String profile = memoryService.profile(credential.get().phoneNumber());
        if (!StringUtils.hasText(profile)) {
            return new AgentCommand(null);
        }

        List<Message> messages = new ArrayList<>(previousMessages.size() + 1);
        messages.add(new SystemMessage(MARKER + " 以下是跨会话记住的用户信息，仅在相关时使用；不要向用户复述或解释这份摘要。\n" + profile));
        messages.addAll(previousMessages);
        log.info("用户画像已注入, thread={}, memories={}", parentThread, profile.lines().count());
        return new AgentCommand(messages, UpdatePolicy.REPLACE);
    }

    @Override
    public String getName() {
        return "user-profile-hook";
    }
}