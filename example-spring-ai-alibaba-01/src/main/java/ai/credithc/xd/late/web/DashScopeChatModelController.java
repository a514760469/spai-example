package ai.credithc.xd.late.web;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * @author zhanglifeng
 * @since 2025-03-18
 */
@RestController
public class DashScopeChatModelController {

    private static final String DEFAULT_PROMPT = "你好，介绍下你自己吧。";

    private final ChatModel dashScopeChatModel;

    private final ChatClient dashScopeChatClient;

    public DashScopeChatModelController(ChatModel chatModel, ChatClient.Builder chatClientBuilder) {

        this.dashScopeChatModel = chatModel;
        // 构造时，可以设置 ChatClient 的参数
        // {@link org.springframework.ai.chat.client.ChatClient};
        this.dashScopeChatClient = ChatClient.builder(chatModel)
                // 实现 Chat Memory 的 Advisor
                // 在使用 Chat Memory 时，需要指定对话 ID，以便 Spring AI 处理上下文。
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(DashScopeChatOptions.builder().withTopP(0.7).build())
                .build();

    }

    @GetMapping("/simple/chat")
    public String simpleChat() {

        return dashScopeChatModel.call(new Prompt(DEFAULT_PROMPT)).getResult().getOutput().getText();
    }

    /**
     * Stream 流式调用。可以使大模型的输出信息实现打字机效果。
     * @return Flux<String> types.
     */
    @GetMapping("/stream/chat")
    public Flux<String> streamChat(HttpServletResponse response) {

        // 避免返回乱码
        response.setCharacterEncoding("UTF-8");

        Flux<ChatResponse> stream = dashScopeChatModel.stream(new Prompt(DEFAULT_PROMPT));
        return stream.map(resp -> resp.getResult().getOutput().getText());
    }

    /**
     * ChatClient 使用自定义的 Advisor 实现功能增强.
     * eg:
     * http://127.0.0.1:10001/dashscope/chat-client/advisor/chat/123/你好，我叫牧生，之后的会话中都带上我的名字
     * 你好，牧生！很高兴认识你。在接下来的对话中，我会记得带上你的名字。有什么想聊的吗？
     * http://127.0.0.1:10001/dashscope/chat-client/advisor/chat/123/我叫什么名字？
     * 你叫牧生呀。有什么事情想要分享或者讨论吗，牧生？
     */
    @GetMapping("/advisor/chat/{id}/{prompt}")
    public Flux<String> advisorChat(
            HttpServletResponse response,
            @PathVariable String id,
            @PathVariable String prompt) {

        response.setCharacterEncoding("UTF-8");


        return this.dashScopeChatClient.prompt(prompt)
                .advisors(a ->
                        a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, id)
                         .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                ).stream().content();
    }


    /**
     * deep seek 的 Reasoning Content
     * model: deepseek-r1
     */
    @GetMapping("/ds/{prompt}")
    public String chat(@PathVariable(value = "prompt") String prompt) {

        ChatResponse chatResponse = dashScopeChatModel.call(new Prompt(prompt));

        if (!chatResponse.getResults().isEmpty()) {
            Map<String, Object> metadata = chatResponse.getResults()
                    .getFirst()
                    .getOutput()
                    .getMetadata();

            System.out.println(metadata.get("reasoningContent"));
        }
        return chatResponse.getResult().getOutput().getText();
    }
}
