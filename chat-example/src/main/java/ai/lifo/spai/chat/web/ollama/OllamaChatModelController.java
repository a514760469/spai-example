package ai.lifo.spai.chat.web.ollama;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/ollama/model")
@RequiredArgsConstructor
public class OllamaChatModelController {

    private static final String DEFAULT_PROMPT = "介绍一下自己";

    private final ChatModel ollamaChatModel;

    /**
     * 简单的聊天示例
     * call 调用
     *
     * @return String types
     */
    @GetMapping("/simple/chat")
    public String simpleChat(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {

        ChatResponse call = ollamaChatModel.call(new Prompt(msg));
        return call.getResult().getOutput().getText();
    }

    /**
     * Stream 流式调用。可以使大模型的输出信息实现打字机效果。
     * 需要避免返回乱码 produces = "text/plain;charset=UTF-8"
     * @param msg 消息
     * @return Flux<String> types.
     */
    @GetMapping(value = "/stream/chat", produces = "text/plain;charset=UTF-8")
    public Flux<String> streamChat(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {

        Flux<ChatResponse> stream = ollamaChatModel.stream(new Prompt(msg));

        return stream.mapNotNull(resp -> resp.getResult().getOutput().getText());
    }

    /**
     * Stream 流式调用。可以使大模型的输出信息实现打字机效果。
     * 需要避免返回乱码
     * @param msg 消息
     * @return Flux<String> types.
     */
    @GetMapping("/stream/doChat")
    public Flux<String> stream(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {

        return ollamaChatModel.stream(msg);
    }

    /**
     * 演示如何获取 LLM 得 token 信息
     */
    @GetMapping("/tokens")
    public Map<String, Object> tokens() {

        Prompt prompt = new Prompt(DEFAULT_PROMPT);
        ChatResponse chatResponse = ollamaChatModel.call(prompt);
        Map<String, Object> res = new HashMap<>();
        res.put("output", chatResponse.getResult().getOutput().getText());
        res.put("output_token", chatResponse.getMetadata().getUsage().getCompletionTokens());
        res.put("input_token", chatResponse.getMetadata().getUsage().getPromptTokens());
        res.put("total_token", chatResponse.getMetadata().getUsage().getTotalTokens());

        return res;
    }

}
