package ai.lifo.agent.graph.translate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 扩展查询节点（流式）：仅负责把 LLM 流式响应丢给框架。
 * 框架会自动订阅 Flux、逐 token 透传给 graph.stream() 消费者，
 * 并在流结束后把聚合结果写回 STREAM_OUTPUT_KEY。
 *
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class ExpanderNode implements NodeAction {

    public static final String NODE_NAME = "expander";

    /** 流式聚合后的完整文本写入此 key（供下游 ExpanderProcessNode 消费） */
    public static final String STREAM_OUTPUT_KEY = "expander_raw";

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate(
            """
            You are an expert at information retrieval and search optimization.
            Your task is to generate {number} different versions of the given query.
            Each variant must cover different perspectives or aspects of the topic,
            while maintaining the core intent of the original query. The goal is to
            expand the search space and improve the chances of finding relevant information.
            Do not explain your choices or add any other text.
            Provide the query variants separated by newlines.

            Original query: {query}

            Query variants:
            """
    );

    private final ChatClient chatClient;
    private static final Integer DEFAULT_NUMBER = 3;

    public ExpanderNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("expander node is running.");
        String query = state.value("query", "");
        Integer expanderNumber = state.value("expandernumber", DEFAULT_NUMBER);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(DEFAULT_PROMPT_TEMPLATE.getTemplate())
                        .param("number", expanderNumber)
                        .param("query", query))
                .stream()
                .chatResponse();

        // Flux 必须作为 Map 顶层 value，框架才会自动订阅并聚合
        // 保证 human_feedback 每轮都能重新中断
        return Map.of(STREAM_OUTPUT_KEY, chatResponseFlux, HumanFeedbackDispatcher.STATE_KEY, OverAllState.MARK_FOR_REMOVAL);
    }
}