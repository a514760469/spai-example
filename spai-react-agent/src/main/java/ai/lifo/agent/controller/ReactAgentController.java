package ai.lifo.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zhanglifeng
 * @since 2026-10-10
 */
@Slf4j
@RestController
@RequestMapping("/api/react-agent")
@RequiredArgsConstructor
public class ReactAgentController {

    private final ChatModel chatModel;

    @GetMapping("/stream")
    public Flux<String> stream(String question) throws GraphRunnerException {

        ReactAgent agent = ReactAgent.builder()
                .name("react-agent")
                .enableLogging(true)
                .model(chatModel)
                .build();

        AtomicBoolean thinkingStart = new AtomicBoolean(false);
        AtomicBoolean thinkingEnd = new AtomicBoolean(false);

        return agent.stream(question).map(nodeOutput -> {
            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                OutputType outputType = streamingOutput.getOutputType();
                if (OutputType.AGENT_MODEL_STREAMING.equals(outputType)) {
                    Message message = streamingOutput.message();
                    String thinkingContent = (String) message.getMetadata().get("reasoningContent");
                    if (StringUtils.hasText(thinkingContent)) {
                        // 输出思考内容
                        log.info("思考内容：{}", thinkingContent);
                        if (!thinkingStart.get()) {
                            thinkingStart.set(true);
                            return "[thinking]" + thinkingContent;
                        }
                        return thinkingContent;
                    }
                    String text = message.getText();
                    if (StringUtils.hasText(text) && !thinkingEnd.get()) {
                        thinkingEnd.set(true);
                        return "[/thinking]" + text;
                    }
                    return text == null ? "" : text;
                }
            }
            return "";
        });
    }
}
