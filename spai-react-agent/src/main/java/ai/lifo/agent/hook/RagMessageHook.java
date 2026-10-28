package ai.lifo.agent.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 在模型调用前检索文档并添加到消息中
 *
 * @author zhanglifeng
 * @since 2026-10-23
 */
@HookPositions({HookPosition.BEFORE_MODEL})
public class RagMessageHook extends MessagesModelHook {

    private final VectorStore vectorStore;

    private static final int TOP_K = 5;

    public RagMessageHook(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String getName() {
        return "ragMessageHook";
    }


    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {

        // 从消息中提取用户问题
        String userQuestion = extractUserQuestion(previousMessages);
        if (userQuestion == null || userQuestion.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        // Step 1: 检索相关文档
        List<Document> relevantDocs = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(userQuestion)
                        .topK(TOP_K)
                        .build()
        );


        // Step 2: 构建上下文
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // Step 3: 构建增强的消息列表
        List<Message> enhancedMessages = new ArrayList<>();

        // 添加系统提示（包含检索到的上下文）
        String systemPrompt = String.format("""
                你是一个有用的助手。基于以下上下文回答问题。
                如果上下文中没有相关信息，请说明你不知道。
                
                上下文：
                %s
                """, context);
        enhancedMessages.add(new SystemMessage(systemPrompt));

        // 保留原有的消息
        enhancedMessages.addAll(previousMessages);

        // 使用 REPLACE 策略替换消息
        return new AgentCommand(enhancedMessages, UpdatePolicy.REPLACE);
    }


    private String extractUserQuestion(List<Message> messages) {
        // 从消息列表中提取最后一个用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return ((UserMessage) msg).getText();
            }
        }
        return null;
    }
}
