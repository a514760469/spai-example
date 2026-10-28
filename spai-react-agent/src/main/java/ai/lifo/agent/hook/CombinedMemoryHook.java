package ai.lifo.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 组合记忆hook 结合短期和长期记忆
 * @author zhanglifeng
 * @since 2026-10-20
 */
public class CombinedMemoryHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "CombinedMemoryHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        Optional<Object> userIdOpt = config.metadata("user_id");
        if (userIdOpt.isEmpty()) {
            return new AgentCommand(previousMessages);
        }
        String userId = (String) userIdOpt.get();
        Store memoryStore = config.store();

        // 从长期记忆加载
        Optional<StoreItem> profileOpt = memoryStore.getItem(List.of("profiles"), userId);
        if (profileOpt.isEmpty()) {
            return new AgentCommand(previousMessages);
        }
        Map<String, Object> profile = profileOpt.get().getValue();
        String contextInfo = String.format("长期记忆：用户 %s, 职业: %s",
                profile.get("name"), profile.get("occupation"));

        return HookUtil.replaceSystemMessage(previousMessages, contextInfo);
    }

    @Override
    public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
        return super.afterModel(previousMessages, config);
    }
}
