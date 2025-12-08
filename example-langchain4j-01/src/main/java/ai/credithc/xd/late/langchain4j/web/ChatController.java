package ai.credithc.xd.late.langchain4j.web;

import ai.credithc.xd.late.langchain4j.ai.service.Assistant;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2025-05-16
 */
@RestController
public class ChatController {

    @Autowired
    Assistant assistant;


    @GetMapping("/chat")
    public String model(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return assistant.chat(message);
    }
}
