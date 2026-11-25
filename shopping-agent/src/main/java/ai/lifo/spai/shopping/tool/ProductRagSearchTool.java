package ai.lifo.spai.shopping.tool;

import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author zhanglifeng
 * @since 2026-11-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRagSearchTool {

    /** 向量召回宽度：粗召回后交给 rerank 精排，两阶段检索的常规比例 */
    private static final int RECALL_TOP_K = 20;

    /** 回注模型 prompt 的条数，直接决定本工具的 token 成本 */
    private static final int RETURN_TOP_N = 5;

    private static final int SNIPPET_MAX_CHARS = 120;

    private final VectorStore vectorStore;

    private final ObjectMapper objectMapper;

    private final RerankModel rerankModel;

    private final MeterRegistry meterRegistry;

    /**
     * @param query    中文自然语言需求（模型自己改写，如"送女友 生日 300以内 饰品"）
     * @param category 可选，硬过滤（Redis TAG: category == x）
     * @param maxPrice 可选，硬过滤（Redis NUMERIC: price <= maxPrice）
     */
    @Tool(name = "productRagSearch", description = """
            语义商品检索：用中文自然语言描述需求（场景/用途/送礼对象等），支持按类目和价格上限过滤。\
            返回候选商品的 productId/title/price 摘要；这是导购召回工具，不含实时库存，\
            选中后用 getProductDetails 核实、再走下单链路。RAG 不可用时会返回 error，此时改用 searchProducts。""")
    public String productRagSearch(String query, String category, BigDecimal maxPrice) {
        log.info("productRagSearch query: {}, category: {}, maxPrice: {}", query, category, maxPrice);

//        if (1 == 1) {
//            return forTest();
//        }
        try {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(RECALL_TOP_K);
            Filter.Expression filter = buildFilter(category, maxPrice);
            if (filter != null) {
                searchBuilder.filterExpression(filter);
            }

            List<Document> recalled = vectorStore.similaritySearch(searchBuilder.build());
            log.info("向量召回 {} 条", recalled.size());
            if (recalled.isEmpty()) {
                meterRegistry.counter("shopping.rag.events", "event", "empty").increment();
                return objectMapper.writeValueAsString(Map.of(
                        "status", "empty",
                        "message", "语义索引无匹配商品，可换个说法重试或改用 searchProducts 关键词搜索"));
            }

            RerankRequest rerankRequest = new RerankRequest(query, recalled);
            RerankResponse response = rerankModel.call(rerankRequest);

            List<RagProductHit> hits = response.getResults().stream()
                    .limit(RETURN_TOP_N)
                    .map(result -> toHit(result.getOutput(), result.getScore()))
                    .toList();
            meterRegistry.counter("shopping.rag.events", "event", "ok").increment();
            return objectMapper.writeValueAsString(hits);
        }
        catch (Exception e) {
            log.error("productRagSearch error", e);
            meterRegistry.counter("shopping.rag.events", "event", "error").increment();
            return "{\"status\":\"error\",\"message\":\"语义检索暂不可用，请改用 searchProducts 关键词搜索\"}";
        }
    }

    private String forTest() {
        return "{\"status\":\"error\",\"message\":\"语义检索暂不可用，请改用 searchProducts 关键词搜索\"}";
    }

    /**
     * 用 AST 而非拼接 filterExpression 字符串：null 参数自然缺席、TAG 值加引号由转换器负责、无表达式注入面。
     */
    private Filter.Expression buildFilter(String category, BigDecimal maxPrice) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        if (StringUtils.hasText(category)) {
            conditions.add(b.eq("category", category));
        }
        if (maxPrice != null) {
            conditions.add(b.lte("price", maxPrice.doubleValue()));
        }
        return conditions.stream()
                .reduce(b::and)
                .map(FilterExpressionBuilder.Op::build)
                .orElse(null);
    }

    /**
     * 检索结果从 Document.metadata 取字段（RedisVectorStore.toDocument 只回传声明过的 metadataFields，
     * 且值一律为 String）
     */
    private RagProductHit toHit(Document document, Double score) {
        Map<String, Object> metadata = document.getMetadata();
        String text = document.getText() == null ? "" : document.getText();
        Object price = metadata.get("price");
        return new RagProductHit(
                Objects.toString(metadata.get("productId"), ""),
                Objects.toString(metadata.get("title"), ""),
                Objects.toString(metadata.get("category"), ""),
                Objects.toString(metadata.get("brand"), ""),
                price == null ? null : new BigDecimal(price.toString()),
                text.length() > SNIPPET_MAX_CHARS ? text.substring(0, SNIPPET_MAX_CHARS) : text,
                score);
    }

    record RagProductHit(
            String productId,
            String title,
            String category,
            String brand,
            BigDecimal price,
            String snippet,
            Double rerankScore
    ) { }
}
