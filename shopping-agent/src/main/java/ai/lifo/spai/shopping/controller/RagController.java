package ai.lifo.spai.shopping.controller;

import ai.lifo.spai.shopping.rag.ProductIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-11-23
 */
@RestController
@RequiredArgsConstructor
public class RagController {

    private final ProductIndexService productIndexService;

    @PostMapping("/rag/reindex")
    public Map<String, Object> reindex() {
        long start = System.currentTimeMillis();
        int indexed = productIndexService.reindex();
        return Map.of("indexed", indexed, "tookMs", System.currentTimeMillis() - start);
    }

    @GetMapping("/rag/stats")
    public ProductIndexService.IndexStats stats() {
        return productIndexService.stats();
    }
}
