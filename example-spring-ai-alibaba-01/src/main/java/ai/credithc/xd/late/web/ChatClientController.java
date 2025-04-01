package ai.credithc.xd.late.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author zhanglifeng
 * @since 2025-03-20
 */
@RestController
@RequestMapping("/client")
public class ChatClientController {

    private final ChatClient chatClient;

    public ChatClientController(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem("You are a friendly chat bot that answers question in the voice of a {voice}")
                .build();
    }

    @GetMapping("/stream/chat")
    public Flux<String> streamChat(@RequestParam String voice) {

        return chatClient.prompt()
                .advisors(new SimpleLoggerAdvisor())
                .user("Tell me a joke")
                .system(p -> p.param("voice", voice))
                .stream()
                .content();
    }

}
