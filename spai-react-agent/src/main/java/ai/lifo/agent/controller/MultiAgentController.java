package ai.lifo.agent.controller;

import ai.lifo.agent.multi.CustomMergeStrategy;
import ai.lifo.agent.prompt.SystemPrompt;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author zhanglifeng
 * @since 2026-10-20
 */
@Slf4j
@RestController
@RequestMapping("/multi")
@RequiredArgsConstructor
public class MultiAgentController {

    private final ChatModel chatModel;

    /**
     * 运行多Agent
     * question=帮我写一个100字左右的散文
     */
    @GetMapping("/seq")
    public Flux<String> getMessage(String question) throws GraphRunnerException {
        // 创建专业化的子Agent
        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(chatModel)
                .description("专业写作Agent")
                .instruction("你是一个知名的作家，擅长写作和创作。请根据用户的提问进行回答：{input}。")
                .outputKey("article")
                // 是否返回推理内容
                .returnReasoningContents(true)
                .build();

        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer_agent")
                .model(chatModel)
                .description("专业评审Agent")
                .instruction("""    
                        你是一个知名的评论家，擅长对文章进行评论和修改。
                        对于散文类文章，请确保文章中必须包含对于西湖风景的描述。待评论文章：
                        {article}
                        最终只返回修改后的文章，不要包含任何评论信息。""")
                .outputKey("reviewed_article")
                .includeContents(true)
                .returnReasoningContents(true)
//                .tools(AgentTool.getFunctionToolCallback(writerAgent))
                .build();

        // 创建顺序Agent
        SequentialAgent blogAgent = SequentialAgent.builder()
                .name("sequential_agent")
                .description("顺序执行Agent")
                .subAgents(List.of(writerAgent, reviewerAgent))
                .build();

        Optional<OverAllState> result = blogAgent.invoke(question);

        if (result.isPresent()) {
            OverAllState state = result.get();

            // 访问第一个Agent的输出
            state.value("article").ifPresent(article -> {
                if (article instanceof AssistantMessage) {
                    System.out.println("原始文章: " + ((AssistantMessage) article).getText());
                }
            });

            // 访问第二个Agent的输出
            state.value("reviewed_article").ifPresent(reviewedArticle -> {
                if (reviewedArticle instanceof AssistantMessage) {
                    System.out.println("评审后文章: " + ((AssistantMessage) reviewedArticle).getText());
                }
            });

        }
        return Flux.just("Hello, World!");
    }

    /**
     * 运行多Agent
     * question=以'西湖'为主题
     */
    @GetMapping("/parallel")
    public Flux<String> getParallelMessage(String question) throws GraphRunnerException {
        // 创建多个专业化Agent
        ReactAgent proseWriterAgent = ReactAgent.builder()
                .name("prose_writer_agent")
                .model(chatModel)
                .description("专门写散文的AI助手")
                .instruction("你是一个知名的散文作家，擅长写优美的散文。" +
                        "用户会给你一个主题：{input}，你只需要创作一篇100字左右的散文。")
                .outputKey("prose_result")
                .enableLogging(true)
                .build();

        ReactAgent poemWriterAgent = ReactAgent.builder()
                .name("poem_writer_agent")
                .model(chatModel)
                .description("专门写现代诗的AI助手")
                .instruction("你是一个知名的现代诗人，擅长写现代诗。" +
                        "用户会给你的主题是：{input}，你只需要创作一首现代诗。")
                .outputKey("poem_result")
                .enableLogging(true)
                .build();

        ReactAgent summaryAgent = ReactAgent.builder()
                .name("summary_agent")
                .model(chatModel)
                .description("专门做内容总结的AI助手")
                .instruction("你是一个专业的内容分析师，擅长对主题进行总结和提炼。" +
                        "用户会给你一个主题：{input}，你只需要对这个主题进行简要总结。")
                .outputKey("summary_result")
                .enableLogging(true)
                .build();

        ParallelAgent parallelAgent = ParallelAgent.builder().subAgents(List.of(proseWriterAgent, poemWriterAgent, summaryAgent))
                .name("parallel_creative_agent")
                .description("并行执行多个创作任务，包括写散文、写诗和做总结")
                .mergeOutputKey("merged_results")
                .mergeStrategy(new CustomMergeStrategy())
                .build();


        ExecutorService executorService = Executors.newFixedThreadPool(3);
        RunnableConfig config = RunnableConfig.builder()
                .addParallelNodeExecutor("parallel_creative_agent", executorService)
                .build();

        Flux<NodeOutput> stream = parallelAgent.stream(question, config);
        return stream.doOnNext(nodeOutput -> {
            System.out.println("节点输出: " + JSON.toJSONString(nodeOutput, SerializerFeature.PrettyFormat));
        }).doOnError(error -> {
            System.err.println("执行出错: " + error.getMessage());
        }).map(nodeOutput -> {
            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                Message message = streamingOutput.message();
                if (message != null) {
                    return message.getText();
                }
                return "";
            }
            return "";
        });
    }

    /**
     * 混合类型Agent
     */
    @GetMapping("/hybrid")
    public Flux<String> hybrid(String question) throws GraphRunnerException {
        // 创建研究Agent（并行执行）
        ReactAgent webResearchAgent = ReactAgent.builder()
                .name("web_research")
                .model(chatModel)
                .description("从互联网搜索信息")
                .instruction("请搜索并收集关于以下主题的信息：{input}")
                .outputKey("web_data")
                .build();

        ReactAgent dbResearchAgent = ReactAgent.builder()
                .name("db_research")
                .model(chatModel)
                .description("从数据库查询信息")
                .instruction("请从数据库中查询并收集关于以下主题的信息：{input}")
                .outputKey("db_data")
                .build();

        ParallelAgent researchAgent = ParallelAgent.builder()
                .name("parallel_research")
                .description("并行收集多个数据源的信息")
                .subAgents(List.of(webResearchAgent, dbResearchAgent))
                .mergeOutputKey("research_data")
                .build();

        // 创建分析Agent
        ReactAgent analysisAgent = ReactAgent.builder()
                .name("analysis_agent")
                .model(chatModel)
                .description("分析研究数据")
                .instruction("请分析以下收集到的数据并提供见解：{research_data}")
                .outputKey("analysis_result")
                .build();

        // 创建报告Agent（路由选择格式）
        ReactAgent pdfReportAgent = ReactAgent.builder()
                .name("pdf_report")
                .model(chatModel)
                .description("生成PDF格式报告")
                .instruction("""
						请根据研究结果和分析结果生成一份PDF格式的报告。
						
						研究结果：{research_data}
						分析结果：{analysis_result}
						""")
                .outputKey("pdf_report")
                .build();

        ReactAgent htmlReportAgent = ReactAgent.builder()
                .name("html_report")
                .model(chatModel)
                .description("生成HTML格式报告")
                .instruction("""
						请根据研究结果和分析结果生成一份HTML格式的报告。
						
						研究结果：{research_data}
						分析结果：{analysis_result}
						""")
                .outputKey("html_report")
                .build();

        LlmRoutingAgent reportAgent = LlmRoutingAgent.builder()
                .name("report_router")
                .description("根据需求选择报告格式")
                .model(chatModel)
                .subAgents(List.of(pdfReportAgent, htmlReportAgent))
                .build();

        // 组合成顺序工作流
        SequentialAgent hybridWorkflow = SequentialAgent.builder()
                .name("research_workflow")
                .description("完整的研究工作流：并行收集 -> 分析 -> 路由生成报告")
                .subAgents(List.of(researchAgent, analysisAgent, reportAgent))
                .build();

        // 打印工作流图表
        OutputUtil.printGraphRepresentation(hybridWorkflow);

//        Flux<NodeOutput> stream = hybridWorkflow.stream(question);
//        return stream.map(nodeOutput -> {
//            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
//                return OutputUtil.obtainThinkingOutputStreaming(streamingOutput);
//            }
//            return "";
//        });
        return Flux.just("Hello, World!");
    }

    /**
     * 智能体路由 Agent
     * 帮我写一篇关于春天的散文
     * 请帮我修改这篇文章：春天来了，花开了。
     * 请将以下内容翻译成英文：春暖花开
     */
    @GetMapping("/route")
    public Flux<String> getRouteMessage(String question) throws GraphRunnerException {
        // 创建专业化的子Agent
        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(chatModel)
                .description("擅长创作各类文章，包括散文、诗歌等文学作品")
                .instruction("你是一个知名的作家，擅长写作和创作。请根据用户的提问进行回答。")
                .outputKey("writer_output")
                .build();

        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer_agent")
                .model(chatModel)
                .description("擅长对文章进行评论、修改和润色")
                .instruction("你是一个知名的评论家，擅长对文章进行评论和修改。对于散文类文章，请确保文章中必须包含对于西湖风景的描述。")
                .outputKey("reviewer_output")
                .build();

        ReactAgent translatorAgent = ReactAgent.builder()
                .name("translator_agent")
                .model(chatModel)
                .description("擅长将文章翻译成各种语言")
                .instruction("你是一个专业的翻译家，能够准确地将文章翻译成目标语言。")
                .outputKey("translator_output")
                .build();

        // 创建路由Agent
        LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
                .name("content_routing_agent")
                .description("根据用户需求智能路由到合适的专家Agent")
                .model(chatModel)
                .systemPrompt(SystemPrompt.ROUTING_SYSTEM_PROMPT)
                .instruction(SystemPrompt.ROUTING_SYSTEM_INSTRUCTION)
                .subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
                .build();

        Flux<NodeOutput> stream = routingAgent.stream(question);
        return stream.doOnNext(nodeOutput -> {
            System.out.println("节点输出: " + JSON.toJSONString(nodeOutput, SerializerFeature.PrettyFormat));
        }).doOnError(error -> {
            System.err.println("执行出错: " + error.getMessage());
        }).map(nodeOutput -> {
            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                return OutputUtil.obtainThinkingOutputStreaming(streamingOutput);
            }
            return "";
        });
    }

}
