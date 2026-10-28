package ai.lifo.agent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-10-20
 */
public class HookUtil {

    @NotNull
    public static AgentCommand replaceSystemMessage(List<Message> previousMessages, String userContext) {
        // 查找是否已存在 SystemMessage
        SystemMessage existingSystemMessage = null;
        int systemMessageIndex = -1;
        for (int i = 0; i < previousMessages.size(); i++) {
            Message msg = previousMessages.get(i);
            if (msg instanceof SystemMessage) {
                existingSystemMessage = (SystemMessage) msg;
                systemMessageIndex = i;
                break;
            }
        }

        // 如果找到 SystemMessage，更新它；否则创建新的
        SystemMessage enhancedSystemMessage;
        if (existingSystemMessage != null) {
            // 更新现有的 SystemMessage
            enhancedSystemMessage = new SystemMessage(existingSystemMessage.getText() + "\n\n" + userContext);
        } else {
            // 创建新的 SystemMessage
            enhancedSystemMessage = new SystemMessage(userContext);
        }

        // 构建新的消息列表
        List<Message> newMessages = new ArrayList<>();
        if (systemMessageIndex >= 0) {
            // 如果找到了 SystemMessage，替换它
            for (int i = 0; i < previousMessages.size(); i++) {
                if (i == systemMessageIndex) {
                    newMessages.add(enhancedSystemMessage);
                }
                else {
                    newMessages.add(previousMessages.get(i));
                }
            }
        }
        else {
            // 如果没有找到 SystemMessage，在开头添加新的
            newMessages.add(enhancedSystemMessage);
            newMessages.addAll(previousMessages);
        }
        // 使用 REPLACE 策略替换所有消息
        return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
    }
}
