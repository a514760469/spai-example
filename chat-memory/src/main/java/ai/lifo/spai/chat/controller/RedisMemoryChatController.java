package ai.lifo.spai.chat.controller;

import com.alibaba.cloud.ai.memory.redis.LettuceRedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * @author zhanglifeng
 * @since 2026-09-28
 */
@RestController
@RequestMapping("/memory/redis")
public class RedisMemoryChatController {

    private static final String DEFAULT_PROMPT = "2加3等于几？";

    private final ChatClient chatClient;

    private final MessageWindowChatMemory messageWindowChatMemory;

    public RedisMemoryChatController(ChatClient.Builder builder, LettuceRedisChatMemoryRepository redisChatMemoryRepository) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(100)
                .build();

        this.messageWindowChatMemory = chatMemory;
        this.chatClient = builder.defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 简单的聊天示例
     * call 调用
     *
     * @return String types
     */
    @GetMapping("/chat")
    public String simpleChat(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg,
                             @RequestParam(defaultValue = "xiaoCaiBi") String conversationId) {
        return chatClient.prompt(msg).advisors(a -> a.param(CONVERSATION_ID, conversationId)).call().content();
    }

    @GetMapping("/messages")
    public List<Message> messages(@RequestParam(value = "conversationId", defaultValue = "yang") String conversationId) {
        return messageWindowChatMemory.get(conversationId);
    }

}
