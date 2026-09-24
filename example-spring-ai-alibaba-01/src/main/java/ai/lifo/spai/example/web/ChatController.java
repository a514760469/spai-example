package ai.lifo.spai.example.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
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
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
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
