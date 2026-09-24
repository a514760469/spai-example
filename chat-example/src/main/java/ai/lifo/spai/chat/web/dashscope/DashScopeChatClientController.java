package ai.lifo.spai.chat.web.dashscope;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/dashscope/client")
@RequiredArgsConstructor
public class DashScopeChatClientController {

    private static final String DEFAULT_PROMPT = "who are you";

    /**
     * chat client
     */
    private final ChatClient dashScopeChatClient;

    private final ObjectMapper objectMapper;

    /**
     * 简单的聊天示例
     * call 调用
     *
     * @return String types
     */
    @GetMapping("/simple/chat")
    public String simpleChat(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {
        ChatResponse chatResponse = dashScopeChatClient.prompt(msg).call().chatResponse();
        if (chatResponse == null) {
            return "chatResponse is null";
        }
        System.out.println(chatResponse.getMetadata());
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
        return dashScopeChatClient.prompt(msg).stream().content();
    }

    /**
     * Stream 流式调用。可以使大模型的输出信息实现打字机效果。
     * 需要避免返回乱码
     * @param msg 消息
     * @return Flux<String> types.
     */
    @GetMapping("/stream/doChat")
    public Flux<String> stream(@RequestParam(defaultValue = DEFAULT_PROMPT) String msg) {
        return dashScopeChatClient.prompt(msg).stream().content();
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
//                .enableSearchExtension(true)
//                .prependSearchResult(true)
                .searchStrategy("pro")
                .enableCitation(true)
                .citationFormat("[<number>]")
                .build();

        // 有些模型不支持联网搜索，得看看模型是否支持
        var options = DashScopeChatOptions.builder()
                .enableSearch(true)
                .model(DashScopeModel.ChatModel.QWEN_PLUS.getValue())
                .searchOptions(searchOptions)
                .temperature(0.7)
                .build();

        Flux<ChatResponse> stream = dashScopeChatClient.prompt(msg)
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


    /**
     * 图片分析接口通过url
     * @param imageUrl 图片url
     * @code  https://b0.bdstatic.com/ugc/NXepQmWAuuibSIYsU0KmdQdbbea6441a58da41fd410998c9e66225.jpg
     * @return Flux<String> types.
     */
    @GetMapping("/image/analyze/url")
    public String imageAnalyzeUrl(@RequestParam(defaultValue = "分析这张图片的内容") String msg,
                                  @RequestParam String imageUrl) {

        // 注意这里的模型选择 qwen3.8-omni-flash
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model("qwen-vl-max")
                .vlHighResolutionImages(true)
                .temperature(0.7)
                .multiModel(true)
                .build();

        List<Media> mediaList;
        try {
            mediaList = List.of(new Media(MimeTypeUtils.IMAGE_JPEG, new URI(imageUrl)));
        } catch (URISyntaxException e) {
            return "图片分析失败: " + e.getMessage();
        }
        UserMessage userMessage = UserMessage.builder()
                .text(msg)
                .media(mediaList)
                .build();

        return dashScopeChatClient.prompt(new Prompt(userMessage, options)).call().content();
    }

    /**
     * 图片分析接口通过url
     * @param imageUrl 图片url
     * @code  https://b0.bdstatic.com/ugc/NXepQmWAuuibSIYsU0KmdQdbbea6441a58da41fd410998c9e66225.jpg
     * @return Flux<String> types.
     */
    @GetMapping(value = "/image/analyze/url/stream", produces = "text/plain;charset=UTF-8")
    public Flux<String> imageAnalyzeUrlStream(@RequestParam(defaultValue = "分析这张图片的内容") String msg,
                                              @RequestParam String imageUrl) {

        // 开启联网搜索
        var searchOptions = DashScopeApiSpec.SearchOptions.builder()
                .enableSource(true)
                .forcedSearch(true)
                .searchStrategy("pro")
                .enableCitation(true)
                .citationFormat("[<number>]")
                .build();

        // 注意这里的模型选择 qwen3.8-omni-flash qwen-vl-max
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model("qwen3.8-omni-flash")
                .searchOptions(searchOptions)
                .temperature(0.7)
                .multiModel(true)
                .enableThinking(true)
                .build();

        List<Media> mediaList;
        try {
            mediaList = List.of(new Media(MimeTypeUtils.IMAGE_JPEG, new URI(imageUrl)));
        } catch (URISyntaxException e) {
            return Flux.error(e);
        }

        UserMessage userMessage = UserMessage.builder()
                .text(msg)
                .media(mediaList)
                .build();

        return dashScopeChatClient.prompt(new Prompt(userMessage, options)).stream().chatResponse().mapNotNull(resp -> {
            String text = resp.getResult().getOutput().getText();
            System.out.println("text: " + text);

            Object searchInfo = resp.getResult().getOutput().getMetadata().get("search_info");
            if (searchInfo != null) {
                try {
                    System.out.println("Search info: " + objectMapper.writeValueAsString(searchInfo));
                } catch (JsonProcessingException ignore) { }
            }
            return text;
        });
    }


    /**
     * 图片分析接口通过url
     * @param imageUrl 图片url
     * @code  https://b0.bdstatic.com/ugc/NXepQmWAuuibSIYsU0KmdQdbbea6441a58da41fd410998c9e66225.jpg
     * @return Flux<String> types.
     */
    @GetMapping(value = "/image/analyze/url/qwen3max", produces = "text/plain;charset=UTF-8")
    public Flux<String> imageAnalyzeUrlStreamQwen3max(@RequestParam(defaultValue = "分析这张图片的内容") String msg,
                                                      @RequestParam String imageUrl) {

        // 注意这里的模型选择 qwen3.8-omni-flash qwen-vl-max qwen3.8-flash
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model("qwen3.8-flash")
                .temperature(0.7)
                .multiModel(true)
                .enableThinking(true)
                .build();

        List<Media> mediaList;
        try {
            mediaList = List.of(new Media(MimeTypeUtils.IMAGE_JPEG, new URI(imageUrl)));
        } catch (URISyntaxException e) {
            return Flux.error(e);
        }

        UserMessage userMessage = UserMessage.builder()
                .text(msg)
                .media(mediaList)
                .build();

        return dashScopeChatClient.prompt(new Prompt(userMessage, options)).stream().chatResponse().mapNotNull(resp -> {
            String text = resp.getResult().getOutput().getText();
            System.out.println("text: " + text);

            Object searchInfo = resp.getResult().getOutput().getMetadata().get("search_info");
            if (searchInfo != null) {
                try {
                    System.out.println("Search info: " + objectMapper.writeValueAsString(searchInfo));
                } catch (JsonProcessingException ignore) { }
            }
            return text;
        });
    }

}
