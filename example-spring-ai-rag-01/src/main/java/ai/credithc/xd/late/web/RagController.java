package ai.credithc.xd.late.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2025-04-02
 */
@RestController
@RequestMapping("/ai")
public class RagController {

    private final ChatClient chatClient;

    private final VectorStore vectorStore;

    public RagController(ChatClient botChatClient, VectorStore vectorStore) {
        this.chatClient = botChatClient;
        this.vectorStore = vectorStore;
    }


    @PostMapping(value = "/chat", produces = "text/plain; charset=UTF-8")
    public String generation(String userInput) {
        // 发起聊天请求并处理响应
        return chatClient.prompt()
                .user(userInput)
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .call()
                .content();
    }

}
