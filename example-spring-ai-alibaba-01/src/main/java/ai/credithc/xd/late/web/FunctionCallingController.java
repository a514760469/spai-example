package ai.credithc.xd.late.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2025-03-20
 */
@RestController
public class FunctionCallingController {

    private final ChatClient chatClient;

    public FunctionCallingController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/ai/weather")
    public String weather() {
        ChatResponse response = chatClient.prompt()
                .tools("currentWeather")
                .user("What's the weather like in San Francisco?")
                .call()
                .chatResponse();
        return response.getResult().getOutput().getText();
//        ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
//                OpenAiChatOptions.builder().withFunction("CurrentWeather").build())); // (1) Enable the function
    }
}
