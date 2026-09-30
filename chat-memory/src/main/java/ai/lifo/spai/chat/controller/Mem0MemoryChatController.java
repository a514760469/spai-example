package ai.lifo.spai.chat.controller;

import com.alibaba.cloud.ai.memory.mem0.advisor.Mem0ChatMemoryAdvisor;
import com.alibaba.cloud.ai.memory.mem0.core.Mem0ServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2026-09-29
 */
@RequestMapping("/memory/mem0")
@RestController
public class Mem0MemoryChatController {

//    private final ChatClient chatClient;
//
//    private final VectorStore store;
//
//    private final Mem0ServiceClient mem0ServiceClient;
//
//    public Mem0MemoryChatController(ChatClient.Builder builder,
//                                    VectorStore store,
//                                    Mem0ServiceClient mem0ServiceClient) {
//        this.store = store;
//        this.mem0ServiceClient = mem0ServiceClient;
//        this.chatClient = builder
//                .defaultAdvisors(
//                        Mem0ChatMemoryAdvisor.builder(store).build()
//                )
//                .build();
//    }



}
