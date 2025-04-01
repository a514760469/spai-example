package ai.credithc.xd.late.web;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2025-03-19
 */
@RestController
public class OllamaChatModelController {

    private final ChatModel ollamaChatModel;

    public OllamaChatModelController(ChatModel chatModel) {
        this.ollamaChatModel = chatModel;
    }

    @GetMapping("/simple/chat/{prompt}")
    public String simpleChat(@PathVariable String prompt) {
        return ollamaChatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
    }

}
