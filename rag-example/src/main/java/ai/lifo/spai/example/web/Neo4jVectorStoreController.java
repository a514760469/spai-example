package ai.lifo.spai.example.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-02
 */
@Slf4j
@RestController
@RequestMapping("/vector/neo4j")
@RequiredArgsConstructor
public class Neo4jVectorStoreController {

    private final Neo4jVectorStore neo4jVectorStore;

    @GetMapping("/add")
    public String add() {
        log.info("start import data");

        HashMap<String, Object> map = new HashMap<>();
        map.put("id", "12345");
        map.put("year", 2025);
        map.put("name", "foo");
        List<Document> documents = List.of(
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("year", 2024)),
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", map));
        neo4jVectorStore.add(documents);
        return "success";
    }

    @GetMapping("/search")
    public List<Document> search(String query) {
        log.info("start search data");
        return neo4jVectorStore.similaritySearch(SearchRequest.builder().query(query).topK(2).build());
    }

    @GetMapping("/delete-filter")
    public String deleteFilter() {
        log.info("start delete data with filter");
        var b = new FilterExpressionBuilder();
        Filter.Expression expression = b.and(
                b.in("year", 2025, 2024),
                b.eq("name", "foo")
        ).build();

        neo4jVectorStore.delete(expression);
        return "success";
    }
}
