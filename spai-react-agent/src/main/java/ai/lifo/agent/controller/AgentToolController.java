package ai.lifo.agent.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * @author zhanglifeng
 * @since 2026-10-21
 */
@Slf4j
@RestController
@RequestMapping("/agent-tool")
@RequiredArgsConstructor
public class AgentToolController {

    private final ChatModel chatModel;

    @GetMapping("/tool")
    public String agentTool(String question) throws GraphRunnerException {

        // 完整类型化agent
        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(chatModel)
                .description("根据类型化输入写文章，返回类型化输出")
                .instruction("你是一个专业作家。请严格按照输入的 topic（主题）、wordCount（字数）和 style（风格）要求创作文章。请创作文章并返回包含 title、content 和 characterCount 的结构化结果。")
                .inputType(ArticleRequest.class)
                .outputType(ArticleOutput.class)
                .build();

        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("typed_reviewer")
                .model(chatModel)
                .description("完整类型化的评审工具")
                .instruction("对文章进行评审，返回评审意见（comment、approved、suggestions）。")
                .outputType(ReviewOutput.class)
                .build();

        ReactAgent orchestratorAgent = ReactAgent.builder()
                .name("orchestrator")
                .model(chatModel)
                .instruction("协调写作和评审流程。先调用写作工具创作文章，然后调用评审工具进行评审。")
                .tools(
                        AgentTool.getFunctionToolCallback(writerAgent),
                        AgentTool.getFunctionToolCallback(reviewerAgent)
                )
                .build();

        OutputUtil.printGraphRepresentation(orchestratorAgent);

        Optional<OverAllState> result = orchestratorAgent.invoke(question);
        return JSON.toJSONString(result.get().data());
    }

    // 定义输入类型
    public record ArticleRequest(
            String topic,      // 文章主题
            int wordCount,     // 字数要求
            String style       // 文章风格
    ) {}

    // 定义输出类型
    @Data
    public static class ArticleOutput {
        private String title;
        private String content;
        private int characterCount;
    }

    @Data
    public static class ReviewOutput {
        private String comment;
        private boolean approved;
        private List<String> suggestions;
    }

}
