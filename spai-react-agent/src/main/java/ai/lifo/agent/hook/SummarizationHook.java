package ai.lifo.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话摘要hook
 * @author zhanglifeng
 * @since 2026-10-16
 */
@Slf4j
@HookPositions({HookPosition.BEFORE_MODEL})
public class SummarizationHook extends MessagesModelHook {

    private final ChatModel summarizationModel;
    private final int triggerLength;

    public SummarizationHook(ChatModel model, int triggerLength) {
        this.summarizationModel = model;
        this.triggerLength = triggerLength;
    }

    @Override
    public String getName() {
        return "SummarizationHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {

        // 如果消息数量未超过阈值，无需总结
        if (previousMessages.size() <= triggerLength) {
            return new AgentCommand(previousMessages);
        }

        // 生成对话摘要
        String summary = generateSummary(previousMessages);
        log.info("对话摘要「{}」", summary);

        // 查找是否已存在 SystemMessage
        SystemMessage existingSystemMessage = null;
        for (Message msg : previousMessages) {
            if (msg instanceof SystemMessage) {
                existingSystemMessage = (SystemMessage) msg;
                break;
            }
        }

        // 创建摘要 SystemMessage
        String summaryText = "之前对话摘要：" + summary;
        SystemMessage summarySystemMessage;
        if (existingSystemMessage != null) {
            // 如果存在 SystemMessage，追加摘要信息
            summarySystemMessage = new SystemMessage(existingSystemMessage.getText() + "\n\n" + summaryText);
        }
        else {
            // 如果不存在，创建新的
            summarySystemMessage = new SystemMessage(summaryText);
        }
        // 保留最近的几条消息
        int recentCount = Math.min(5, previousMessages.size());
        List<Message> recentMessages = previousMessages.subList(previousMessages.size() - recentCount, previousMessages.size());

        // 构建新的消息列表
        List<Message> newMessages = new ArrayList<>();
        newMessages.add(summarySystemMessage);
        // 添加最近的消息，排除旧的 SystemMessage（如果存在）
        for (Message msg : recentMessages) {
            if (msg != existingSystemMessage) {
                newMessages.add(msg);
            }
        }

        // 替换所有消息
        return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
    }

    private String generateSummary(List<Message> messages) {
        String conversation = messages.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));

        String summarizePrompt = "请将以下对话内容压缩为一段简洁的摘要，保留关键信息和上下文。只输出摘要内容，不要添加额外说明。\n\n对话内容：\n" + conversation;

        Prompt prompt = new Prompt(new UserMessage(summarizePrompt));
        return summarizationModel.call(prompt).getResult().getOutput().getText();
    }
}
