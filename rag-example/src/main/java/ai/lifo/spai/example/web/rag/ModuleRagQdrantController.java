package ai.lifo.spai.example.web.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.web.bind.annotation.*;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * RetrievalAugmentationAdvisor 遵循rag模块化架构
 * Pre-Retrieval
 * 1 QueryAugmenter
 * 2 QueryTransformer
 * 3 QueryExpander
 * Retrieval
 * 1 DocumentRetriever 检索器，根据 QueryExpander 使用不同的数据源进行检索，例如 搜索引擎、向量存储、数据库或知识图等
 * 2 DocumentJoiner 将从多个 query 和从多个数据源检索到的 Document 合并为一个 Document 集合；
 * Post-Retrieval
 * 1 DocumentRanker
 * 2 DocumentSelector
 * 3 DocumentCompressor
 *
 * @author zhanglifeng
 * @since 2026-10-08
 */
@Slf4j
@RestController
@RequestMapping("/module/rag/qdrant")
public class ModuleRagQdrantController {

    private final ChatClient chatClient;

    private final MessageChatMemoryAdvisor chatMemoryAdvisor;

    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    public ModuleRagQdrantController(ChatClient.Builder chatClientBuilder,
                                     ChatMemory chatMemory,
                                     QdrantVectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        var documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.50)
                .build();

        // 使用llm压缩历史对话和当前查询为独立查询
        var compressionQueryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .build();

        // 重写提示词
        var rewriteQueryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .targetSearchSystem("vector store")
                .build();

        // 翻译
        var translationQueryTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .targetLanguage("english")
                .build();

        this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(compressionQueryTransformer, rewriteQueryTransformer, translationQueryTransformer)
                .documentRetriever(documentRetriever)
                .build();
    }

    @PostMapping("/{chatId}")
    public String chatWithDocument(@RequestBody String prompt, @PathVariable("chatId") String conversationId) {
        return chatClient.prompt()
                .advisors(chatMemoryAdvisor, retrievalAugmentationAdvisor)
                .advisors(advisors -> advisors.param(CONVERSATION_ID, conversationId))
                .user(prompt)
                .call()
                .content();
    }

    @GetMapping("/basic")
    public String chatWithDocument(@RequestParam("prompt") String prompt) {

        return chatClient.prompt().advisors(retrievalAugmentationAdvisor).user(prompt).call().content();
    }
}
