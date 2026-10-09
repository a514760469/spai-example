package ai.lifo.spai.example.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-03
 */
@Slf4j
@RestController
@RequestMapping("/vector/redis")
@RequiredArgsConstructor
public class RedisVectorController {

    private final RedisVectorStore redisVectorStore;

    @GetMapping("/import")
    public String add() throws IOException {
        log.info("start import data");
        FileSystemResourceLoader loader = new FileSystemResourceLoader();
        Resource resource1 = loader.getResource("classpath:vector/gc.txt");
        Resource resource2 = loader.getResource("classpath:vector/gc2.txt");
        Resource resource3 = loader.getResource("classpath:vector/gc3.txt");
        Resource resource4 = loader.getResource("classpath:vector/redis.txt");
        List<Document> documents = List.of(
                new Document(resource1.getContentAsString(Charset.defaultCharset()), Map.of("subject", "gc")),
                new Document(resource2.getContentAsString(Charset.defaultCharset()), Map.of("subject", "gc")),
                new Document(resource3.getContentAsString(Charset.defaultCharset()), Map.of("subject", "gc")),
                new Document(resource4.getContentAsString(Charset.defaultCharset()), Map.of("subject", "redis")),
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("id", 123,
                        "year", 2025,
                        "name", "foo")));
        redisVectorStore.add(documents);
        return "success";
    }

    @GetMapping("/search")
    public List<Document> search(String query) {
        log.info("start search data");
        return redisVectorStore.similaritySearch(SearchRequest.builder().query(query).topK(5).build());
    }

    @GetMapping("/delete-filter")
    public String deleteFilter() {
        log.info("start delete data with filter");
        var b = new FilterExpressionBuilder();
        Filter.Expression expression = b.and(
                b.in("year", 2025, 2024),
                b.eq("name", "foo")
        ).build();

        redisVectorStore.delete(expression);
        return "success";
    }

    @GetMapping("/similaritySearch")
    public List<Document> similaritySearch(String query) {

        var b = new FilterExpressionBuilder().eq("subject", "gc").build();
        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThreshold(0.7)
                .filterExpression(b).build();
        return redisVectorStore.similaritySearch(req);
    }
}
