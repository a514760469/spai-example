package ai.lifo.spai.example.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-02
 */
@Slf4j
@RequestMapping("/simple")
@RestController
public class SimpleController {

    private final SimpleVectorStore simpleVectorStore;

    private final String SAVE_PATH = System.getProperty("user.dir") + "/rag-example/src/main/resources/save.json5";

    public SimpleController(EmbeddingModel embeddingModel) {
        this.simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    @GetMapping("/importData")
    public String importData() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("year", 2025);
        map.put("name", "foo");
        List<Document> documents = List.of(
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("year", 2024)),
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", map),
                new Document("1", "test content", map));
        simpleVectorStore.add(documents);
        return "ok";
    }



    @GetMapping("/save")
    public String save() {
        log.info("start save data: {}", SAVE_PATH);
        File file = new File(SAVE_PATH);
        if (file.exists()) {
            file.delete();
        }
        // 序列化到文件中
        simpleVectorStore.save(file);
        return "ok";
    }

    @GetMapping("/delete")
    public String delete(String id) {
        log.info("start delete data");
        // 从文件中反序列化
        simpleVectorStore.delete(List.of(id));
        return "ok";
    }

    @GetMapping("/load")
    public String load() {
        log.info("start load data: {}", SAVE_PATH);
        File file = new File(SAVE_PATH);
        simpleVectorStore.load(file);
        return "ok";
    }

    @GetMapping("/search")
    public List<Document> search(String query) {
        log.info("start search data");

        return simpleVectorStore.similaritySearch(SearchRequest.builder().query(query).topK(2).build());
    }

    @GetMapping("/search-filter")
    public List<Document> searchFilter() {
        log.info("start search  filter data");
        var b = new FilterExpressionBuilder();

        Filter.Expression expression = b.and(
                b.in("year", 2025, 2024),
                b.eq("name", "foo")
        ).build();

        return simpleVectorStore.similaritySearch(SearchRequest
                .builder()
                .query("Spring")
                .topK(2)
                .filterExpression(expression).build());
    }

}
