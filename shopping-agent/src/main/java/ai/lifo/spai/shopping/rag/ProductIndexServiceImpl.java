package ai.lifo.spai.shopping.rag;

import ai.lifo.spai.shopping.config.VectorStoreConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author zhanglifeng
 * @since 2026-11-23
 */
@Slf4j
@Service
public class ProductIndexServiceImpl implements ProductIndexService {

    private static final int PAGE_SIZE = 50;

    /** Redis FT.SEARCH 的 LIMIT 单次上限是 10000 */
    private static final int FT_PAGE_SIZE = 1000;

    private final RestClient restClient;

    private final VectorStore vectorStore;

    private final JedisPooled jedisPooled;

    private final RedisVectorStoreProperties properties;

    private final AtomicReference<IndexStats> lastStats = new AtomicReference<>();

    public ProductIndexServiceImpl(RestClient.Builder builder,
                                   VectorStore vectorStore,
                                   @Value("${product.base-url}") String productBaseUrl,
                                   RedisVectorStoreProperties properties,
                                   JedisPooled jedisPooled) {
        this.restClient = builder.baseUrl(productBaseUrl).build();
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.jedisPooled = jedisPooled;
    }

    @Override
    public int reindex() {
        long start = System.currentTimeMillis();
        List<ProductSummary> products = fetchAllProducts();
        List<Document> documents = products.stream().map(this::toDocument).toList();

        // Document.id=productId：RedisVectorStore.doAdd 按 id upsert，重跑即覆盖而非堆积重复
        vectorStore.add(documents);
        int staleRemoved = deleteStale(products.stream().map(ProductSummary::id).collect(Collectors.toSet()));

        int indexed = documents.size();
        lastStats.set(new IndexStats(indexed, Instant.now(), properties.getIndexName()));
        log.info("商品索引重建完成: indexed={}, staleRemoved={}, tookMs={}",
                indexed, staleRemoved, System.currentTimeMillis() - start);
        return indexed;
    }

    @Override
    public IndexStats stats() {
        IndexStats stats = lastStats.get();
        return stats != null ? stats : new IndexStats(0, null, properties.getIndexName());
    }

    private List<ProductSummary> fetchAllProducts() {
        List<ProductSummary> all = new ArrayList<>();
        int totalPages;
        int page = 0;
        do {
            ProductPage result = restClient.get()
                    .uri("/api/v1/products?page={page}&size={size}", page, PAGE_SIZE)
                    .retrieve()
                    .body(ProductPage.class);
            if (result == null || result.products() == null) {
                throw new IllegalStateException("拉取商品列表失败: page=" + page);
            }
            all.addAll(result.products());
            totalPages = result.totalPages();
            page++;
        } while (page < totalPages);
        return all;
    }

    private Document toDocument(ProductSummary product) {
        // 自然语言组装而非整包 JSON：JSON 键名和数字字段稀释语义密度，还按 token 多花 embedding 成本。
        // 截断修复后 REST 列表自带全量 description，单段式即可。
        String text = "%s | %s > %s | 品牌: %s | %s".formatted(
                product.title(), product.category(), product.subCategory(), product.brand(), product.description());
        return Document.builder()
                .id(product.id())
                .text(text)
                .metadata(Map.of(
                        "productId", product.id(),
                        "title", product.title(),
                        "category", nullToEmpty(product.category()),
                        "subCategory", nullToEmpty(product.subCategory()),
                        "brand", nullToEmpty(product.brand()),
                        "price", product.price() != null ? product.price() : BigDecimal.ZERO,
                        "imageUrl", nullToEmpty(product.imageUrl())))
                .build();
    }

    /** 目录里已下线的商品要从索引清掉，否则 ragSearch 会召回不存在的 productId */
    private int deleteStale(Set<String> currentIds) {
        List<String> staleIds = new ArrayList<>();
        int offset = 0;
        while (true) {
            // FT.SEARCH 的 LIMIT 单次上限 10000，必须分页扫；只回取 productId，不带 content/embedding
            SearchResult searchResult = jedisPooled.ftSearch(properties.getIndexName(),
                    new Query("*").returnFields("productId").limit(offset, FT_PAGE_SIZE));
            List<String> ids = searchResult.getDocuments().stream()
                    .map(doc -> doc.getId().substring(properties.getPrefix().length()))
                    .toList();
            if (ids.isEmpty()) {
                break;
            }
            ids.stream().filter(id -> !currentIds.contains(id)).forEach(staleIds::add);
            if (ids.size() < FT_PAGE_SIZE) {
                break;
            }
            offset += FT_PAGE_SIZE;
        }
        if (!staleIds.isEmpty()) {
            vectorStore.delete(staleIds);
        }
        return staleIds.size();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
