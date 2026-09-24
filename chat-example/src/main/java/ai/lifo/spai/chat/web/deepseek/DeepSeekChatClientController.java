package ai.lifo.spai.chat.web.deepseek;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author zhanglifeng
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/deepseek/client")
@RequiredArgsConstructor
public class DeepSeekChatClientController {

    private static final String DEFAULT_PROMPT = "你好，介绍下你自己！";

    /**
     * chat client
     */
    private final ChatClient deepSeekChatClient;

    private final ObjectMapper objectMapper;

    /**
     * 简单的聊天示例
     * call 调用
     *
     * @return String types
     */
    @GetMapping("/simple/chat")
    public String simpleChat(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {
        ChatResponse chatResponse = deepSeekChatClient.prompt(msg).call().chatResponse();
        if (chatResponse == null) {
            return "chatResponse is null";
        }
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * Stream 流式调用。可以使大模型的输出信息实现打字机效果。
     * 需要避免返回乱码
     * @param msg 消息
     * @return Flux<String> types.
     */
    @GetMapping(value = "/stream/chat", produces = "text/plain;charset=UTF-8")
    public Flux<String> streamChat(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {
        return deepSeekChatClient.prompt(msg).stream().content();
    }

    /**
     * curl localhost:8080/client/stream/searchInfos
     * 联网搜索
     * @return Flux<String> types.
     */
    @GetMapping(value = "/stream/searchInfos", produces = "text/plain;charset=UTF-8")
    public Flux<String> searchInfoStreams(@RequestParam(defaultValue = "搜索下关于量子物理的最新研究进展") String msg) {


        var searchOptions = DashScopeApiSpec.SearchOptions.builder()
                .forcedSearch(true)
                .enableSource(true)
                .searchStrategy("pro")
                .enableCitation(true)
                .citationFormat("[<number>]")
                .build();

        // 有些模型不支持联网搜索，得看看模型是否支持
        var options = DashScopeChatOptions.builder()
                .enableSearch(true)
                .model("deepseek-v4-pro")
                .searchOptions(searchOptions)
                .temperature(0.7)
                .build();

        Flux<ChatResponse> stream = deepSeekChatClient.prompt(msg)
                .options(options)
                .stream()
                .chatResponse();

        return stream.mapNotNull(resp -> {

            String text = resp.getResult().getOutput().getText();
            System.out.println("text: " + text);
            // 打印使用量信息
            if (resp.getMetadata().getUsage() != null) {
                System.out.println("Usage - Completion: " + resp.getMetadata().getUsage().getCompletionTokens());
                System.out.println("Usage - Prompt: " + resp.getMetadata().getUsage().getPromptTokens());
                System.out.println("Usage - Total: " + resp.getMetadata().getUsage().getTotalTokens());
            }
            Object searchInfo = resp.getResult().getOutput().getMetadata().get("search_info");
            if (searchInfo != null) {
                try {
                    System.out.println("Search info: " + objectMapper.writeValueAsString(searchInfo));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            return text;
        });
    }

}
