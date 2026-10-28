package ai.lifo.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.ai.chat.messages.Message;

import java.util.*;

/**
 * 使用 MessagesModelHook 管理长期记忆
 * @author zhanglifeng
 * @since 2026-10-20
 */
public class LongTermMemoryHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "LongTermMemoryHook";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
    }


    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 从配置中获取用户ID
        String userId = (String) config.metadata("user_id").orElse(null);
        if (userId == null) {
            return new AgentCommand(previousMessages);
        }


        Store store = config.store();
        // 从记忆存储中加载用户画像
        Optional<StoreItem> itemOpt = store.getItem(List.of("user_profiles"), userId);
        if (itemOpt.isPresent()) {
            Map<String, Object> profile = itemOpt.get().getValue();
            // 将用户上下文注入系统消息
            String userContext = String.format(
                    "用户信息：姓名=%s, 年龄=%s, 邮箱=%s, 偏好=%s",
                    profile.get("name"),
                    profile.get("age"),
                    profile.get("email"),
                    profile.get("preferences")
            );

            return HookUtil.replaceSystemMessage(previousMessages, userContext);
        }
        return new AgentCommand(previousMessages);
    }

    @Override
    public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
        // 可以在这里实现对话后的记忆保存逻辑
//        Store store = config.store();
//        String userId = (String) config.metadata("user_id").orElse(null);
//        if (userId == null) {
//            return new AgentCommand(previousMessages);
//        }
//
//        // 提取用户输入
//        if (previousMessages.isEmpty()) {
//            return new AgentCommand(previousMessages);
//        }
//
//        // 加载现有偏好
//        Optional<StoreItem> prefsOpt = store.getItem(List.of("user_data"), userId + "_preferences");
//        List<String> prefs = new ArrayList<>();
//        if (prefsOpt.isPresent()) {
//            Map<String, Object> prefsData = prefsOpt.get().getValue();
//            prefs = (List<String>) prefsData.getOrDefault("items", new ArrayList<>());
//        }
//
//        // 简单的偏好提取（实际应用中使用NLP）
//        for (Message msg : previousMessages) {
//            String content = msg.getText().toLowerCase();
//            if (content.contains("喜欢") || content.contains("偏好")) {
//                prefs.add(msg.getText());
//
//                Map<String, Object> prefsData = new HashMap<>();
//                prefsData.put("items", prefs);
//                StoreItem item = StoreItem.of(List.of("user_data"), userId + "_preferences", prefsData);
//                store.putItem(item);
//
//                System.out.println("学习到用户偏好 " + userId + ": " + msg.getText());
//            }
//        }
        return new AgentCommand(previousMessages);
    }
}
