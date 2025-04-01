import ai.credithc.xd.late.SpringAiAlibaba01Application;
import ai.credithc.xd.late.record.ActorFilms;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

/**
 * @author zhanglifeng
 * @since 2025-03-19
 */
@SpringBootTest(classes = SpringAiAlibaba01Application.class)
public class ModelClientTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    public void testModelClient() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ActorFilms entity = chatClient.prompt().user("Generate the filmography for a random actor.")
                .call().entity(ActorFilms.class);

        System.out.println(STR."entity = \{entity}");
    }


    @Test
    public void testStreamingResponse() {

        ChatClient chatClient = ChatClient.builder(chatModel).build();
        Flux<String> output = chatClient.prompt()
                .user("Tell me a joke")
                .stream()
                .content();

        output.subscribe(System.out::println);
    }


    public static void main(String[] args) {



        Flux<String> flux = Flux.just("Apple", "Banana", "Cherry");
        flux.subscribe(System.out::println);
    }
}
