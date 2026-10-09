package ai.lifo.spai.example.web.rag;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.alibaba.cloud.ai.model.RerankModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * pgvector的rag 示例
 *
 * @author zhanglifeng
 * @since 2026-10-04
 */
@Slf4j
@RestController
@RequestMapping("/rag/pgvector")
@RequiredArgsConstructor
public class RagPgVectorController {

    @Value("classpath:/prompts/system-qa.txt")
    private Resource systemResource;

    @Value("classpath:/data/spring_ai_alibaba_quickstart.pdf")
    private Resource springAiResource;

    private final VectorStore pgVectorStore;

    private final ChatModel chatModel;

    private final RerankModel rerankModel;


    @GetMapping("/importDocument")
    public String importDocument() {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(springAiResource);
        List<Document> documents = reader.get();
        // 2. split trunks
        List<Document> splitDocuments = new TokenTextSplitter().apply(documents);

        // 3. create embedding and store to vector store
        pgVectorStore.add(splitDocuments);
        return "success";
    }

    /**
     * Receive any long text, split it and write it into a vector store
     * @param text The text to be inserted
     * @return response
     */
    @GetMapping("/importText")
    public ResponseEntity<String> insertText(@RequestParam("text") String text) {
        // 1.parameter verification
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().body("Please enter text");
        }
        // 2.parse document
        List<Document> documents = List.of(new Document(text));

        // 3.Splitting Text
        List<Document> splitDocuments = new TokenTextSplitter().apply(documents);

        // 4.create embedding and store to vector store
        pgVectorStore.add(splitDocuments);
        // 5.return success prompt
        String msg = String.format("successfully inserted %d text fragments into vector store", splitDocuments.size());
        return ResponseEntity.ok(msg);
    }

    /**
     * read and write multiple files and write it into a vector store
     * @param file The file to be inserted
     * @return response
     */
    @PostMapping(value = "/importFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> insertFiles( @RequestPart(value = "file", required = false) MultipartFile file) {
        // 1. file verification
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("必须上传非空的文件");
        }
        // 2. parse files
        List<Document> docs = new TikaDocumentReader(file.getResource()).get();

        // 3. Splitting Text
        List<Document> splitDocs = new TokenTextSplitter().apply(docs);

        // 4. create embedding and store to vector store
        pgVectorStore.add(splitDocs);

        // 5.return success prompt
        String msg = String.format("successfully inserted %d text fragments into vector store", splitDocs.size());
        return ResponseEntity.ok(msg);
    }

    /**
     * read and write multiple files and write it into a vector store
     * 生成并返回一个fileId，可用于查询过滤文件
     * @param file The file to be inserted
     * @return response
     */
    @PostMapping(value = "/importFileV2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importFileV2(@RequestPart(value = "file", required = false) MultipartFile file) {
        // 1. file verification
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("必须上传非空的文件");
        }
        // 2. parse files
        List<Document> docs = new TikaDocumentReader(file.getResource()).get();

        // 3. Splitting Text
        List<Document> splitDocs = new TokenTextSplitter().apply(docs);

        String fileId = UUID.randomUUID().toString();
        for (Document doc : splitDocs) {
            doc.getMetadata().put("fileId", fileId);
        }

        // 4. create embedding and store to vector store
        pgVectorStore.add(splitDocs);

        // 5.return success prompt
        String msg = String.format("successfully inserted %d text fragments into vector store," +
                " fileId: %s", splitDocs.size(), fileId);
        return ResponseEntity.ok(msg);
    }

    /**
     * delete file by fileId
     * @param fileId file id
     */
    @PostMapping(value = "/deleteFilesV2")
    public ResponseEntity<String> deleteFiles(@RequestParam(value = "fileId", required = false) String fileId) {
        var b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq("fileId", fileId).build();
        pgVectorStore.delete(expression);
        return ResponseEntity.ok("successfully deleted");
    }


    /**
     * rag 查询，saa提供的 com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor 调用 rerankModel 重排序
     * qwen3.7-text-rerank
     * @param message The message to be queried
     * @return response
     */
    @GetMapping(value = "/rag", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> generate(@RequestParam(value = "message",
            defaultValue = "how to get start with spring ai alibaba?") String message) throws IOException {
        SearchRequest searchRequest = SearchRequest.builder().topK(2).build();
        String promptTemplate = systemResource.getContentAsString(StandardCharsets.UTF_8);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(new RetrievalRerankAdvisor(pgVectorStore, rerankModel, searchRequest, new SystemPromptTemplate(promptTemplate), 0.1))
                .build()
                .prompt()
                .user(message)
                .stream()
                .content();
    }

    /**
     * search the vector store, 这里根据文件id精确过滤
     * @param message The message to be queried
     * @param fileId The file id to be searched
     * @return response
     */
    @GetMapping(value = "/rag/searchV2", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public  Flux<String> search(@RequestParam(value = "message", defaultValue = "what is blibaba?") String message,
                                @RequestParam(value = "fileId") String fileId) throws IOException {

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq("fileId", fileId).build();

        SearchRequest searchRequest = SearchRequest.builder().topK(1).filterExpression(expression).build();
        String promptTemplate = systemResource.getContentAsString(StandardCharsets.UTF_8);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(new RetrievalRerankAdvisor(pgVectorStore, rerankModel, searchRequest, new SystemPromptTemplate(promptTemplate), 0.1),
                        new SimpleLoggerAdvisor())
                .build()
                .prompt()
                .user(message)
                .stream()
                .content();
    }
}
