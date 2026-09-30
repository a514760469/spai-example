package ai.lifo.spai.chat.controller;

import ai.lifo.spai.chat.custom.MybatisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 自定义memory chat
 * @author zhanglifeng
 * @since 2026-09-28
 */
@RestController
@RequestMapping("/memory/custom")
public class CustomMemoryChatController {

    private static final String DEFAULT_PROMPT = "2加3等于几？";

    private final ChatClient chatClient;

    private final MessageWindowChatMemory messageWindowChatMemory;

    public CustomMemoryChatController(ChatClient.Builder builder, MybatisChatMemoryRepository mybatisChatMemoryRepository, ChatModel chatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(mybatisChatMemoryRepository)
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
                             @RequestParam(defaultValue = "senior") String conversationId) {

        return chatClient.prompt(msg)
                .advisors(a -> a.param(CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    @GetMapping("/messages")
    public List<Message> messages(@RequestParam(value = "conversationId", defaultValue = "yang") String conversationId) {
        return messageWindowChatMemory.get(conversationId);
    }

}
