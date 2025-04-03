package ai.credithc.xd.late.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2025-04-02
 */
@RestController
public class MultiQueryController {

    private final ChatClient chatClientMultiQuery;

    public MultiQueryController(ChatClient chatClientMultiQuery) {
        this.chatClientMultiQuery = chatClientMultiQuery;
    }
}
