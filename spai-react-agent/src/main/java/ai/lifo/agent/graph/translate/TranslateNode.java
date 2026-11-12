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
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class TranslateNode implements NodeAction {

    private static final PromptTemplate DEFAULTPROMPTTEMPLATE = new PromptTemplate(
            """
            Given a user query, translate it to {targetLanguage}.
            
            If the query is already in {targetLanguage}, return it unchanged.
            
            If you don't know the language of the query, return it unchanged.
            
            Do not add explanations nor any other text.
            
            Original query: {query}

            Translated query:
            """
    );

    private static final String TARGETLANGUAGE = "English";

    private final ChatClient chatClient;

    public TranslateNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        log.info("translate node is running.");

        String query = state.value("query", "");
        String targetLanguage = state.value("translatelanguage", TARGETLANGUAGE);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(DEFAULTPROMPTTEMPLATE.getTemplate())
                        .param("targetLanguage", targetLanguage)
                        .param("query", query))
                .stream()
                .chatResponse();

        return Map.of("translatecontent", chatResponseFlux);
    }
}
