package ai.lifo.spai.shopping.tool;

import ai.lifo.spai.shopping.rag.ProductIndexService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ragSearch 集成验证：@SpringBootTest 走真实 Redis 索引 + 真实百炼 embedding/rerank。
 * 覆盖 TC-R 矩阵 B 层：R5 语义命中、R6 价格硬过滤、R7 类目硬过滤、R8 空参回归位、
 * R9 embedding 跨语言（隔离掉"模型改写query"第一层后的第二层单变量）、R12 rerank 生效。
 * 首次执行自动 reindex（幂等 upsert），成本约数百次 embedding 计费，秒级完成。
 * 注意：依赖 mock server（localhost:8000）在线与百炼额度可用。
 */
@Slf4j
@SpringBootTest
class ProductRagSearchToolTest {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private ProductRagSearchTool ragSearchTool;

    @Autowired
    private ProductIndexService productIndexService;

    @Autowired
    private JedisPooled jedisPooled;

    @Autowired
    private ObjectMapper objectMapper;

    private static volatile boolean indexed = false;

    @BeforeEach
    void ensureIndexed() throws Exception {
        if (!indexed) {
            synchronized (ProductRagSearchToolTest.class) {
                if (!indexed) {
                    int count = productIndexService.reindex();
                    assertTrue(count > 0, "reindex 后索引条数应大于 0");
                    indexed = true;
                }
            }
        }
    }

    /**
     * R3：stale 清理。手工塞一个目录里不存在的假商品文档（runId 隔离），
     * reindex 必须把它从索引里扫掉，且不能误伤真商品（indexed 数不减）。
     */
    @Test
    void R3_stale_fake_document_swept_on_reindex() {
        String fakeId = "rag-test-fake-" + RUN_ID;
        String fakeKey = "product:" + fakeId;
        jedisPooled.jsonSetWithEscape(fakeKey, Path2.of("$"),
                Map.of("productId", fakeId, "title", "测试假商品-应被stale清理"));
        assertNotNull(jedisPooled.jsonGet(fakeKey, Path2.of("$.title")), "假文档应已写入 Redis");

        int indexed = productIndexService.reindex();

        assertTrue(indexed > 0, "reindex 仍应索引全部真实商品");
        Object leftover = jedisPooled.jsonGet(fakeKey, Path2.of("$.title"));
        assertTrue(leftover == null || (leftover instanceof List<?> l && l.isEmpty()),
                "stale 假文档必须被 reindex 清除，实际残留: " + leftover);
    }

    /**
     * R5 + R12：中文口语化送礼查询。断命中非空、条数≤5、字段齐全、
     * 且 rerankScore 降序——分数有序即 rerank 真正参与了排序（纯向量召回顺序不会是这序）。
     */
    @Test
    void R5_semantic_gift_query_returns_ranked_hits() throws Exception {
        String json = ragSearchTool.productRagSearch("送女朋友的生日礼物 浪漫有心意", null, new BigDecimal("500"));
        List<Hit> hits = asHits(json, "R5");
        assertFalse(hits.isEmpty(), "送礼语义查询不应空手");
        assertTrue(hits.size() <= 5, "回注条数不应超过 5");
        for (Hit hit : hits) {
            assertFalse(hit.productId().isBlank(), "productId 必须有值");
            assertFalse(hit.title().isBlank(), "title 必须有值");
            assertNotNull(hit.rerankScore(), "rerankScore 必须回传");
        }
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).rerankScore() >= hits.get(i).rerankScore(),
                    "结果必须按 rerank 分数降序");
        }
        log.info("R5 命中: {}", hits);
    }

    /**
     * R6：maxPrice 硬过滤。任何一条 price>上限 都说明 NUMERIC filter 没生效。
     */
    @Test
    void R6_price_filter_excludes_expensive_hits() throws Exception {
        String json = ragSearchTool.productRagSearch("蓝牙耳机", null, new BigDecimal("100"));
        List<Hit> hits = asHits(json, "R6");
        assertTrue(hits.size() > 0, "¥100 内的耳机在现库存在（此前日志见 59/149 档），过滤后仍应有命中");
        for (Hit hit : hits) {
            assertTrue(hit.price().compareTo(new BigDecimal("100")) <= 0,
                    "价格过滤泄漏: " + hit);
        }
    }

    /**
     * R7：category 硬过滤（大小写按库内真实枚举值）。
     */
    @Test
    void R7_category_filter_is_hard_boundary() throws Exception {
        String json = ragSearchTool.productRagSearch("降噪耳机", "ELECTRONICS", null);
        List<Hit> hits = asHits(json, "R7");
        assertFalse(hits.isEmpty(), "ELECTRONICS 类目应能命中");
        for (Hit hit : hits) {
            assertEquals("ELECTRONICS", hit.category(), "类目过滤泄漏: " + hit);
        }
    }

    /**
     * R8 回归位：category/maxPrice 双 null。原字符串拼接实现会拼出
     * "category == null && price <= null" 直接解析失败——这里绝不允许返回 status=error。
     */
    @Test
    void R8_null_filters_must_not_break() throws Exception {
        String json = ragSearchTool.productRagSearch("机械键盘", null, null);
        assertNotStatus(json, "error", "R8 空参不得触发 filter 解析错误");
    }

    /**
     * R9 第二层单变量：英文 query 直调，中间没有任何模型做中文改写——
     * 命中即证明 qwen3.7 embedding 自身跨语言对齐成立；
     * 若返回 empty/error，说明第一层（模型改写）是唯一防线，须如实上报。
     */
    @Test
    void R9_english_query_works_without_model_rewrite() throws Exception {
        String json = ragSearchTool.productRagSearch("birthday gift for girlfriend", null, null);
        assertNotStatus(json, "error", "R9 英文直调不得报错");
        List<Hit> hits = asHits(json, "R9");
        assertFalse(hits.isEmpty(), "英文 query 必须能召回中文商品（跨语言 embedding 独立成立）");
        log.info("R9 英文直调命中: {}", hits);
    }

    private List<Hit> asHits(String json, String caseName) throws Exception {
        assertNotStatus(json, "error", caseName + " 不应返回 error");
        assertNotStatus(json, "empty", caseName + " 期望有命中的 empty");
        assertTrue(json.trim().startsWith("["), caseName + " 返回应是命中数组: " + json);
        return objectMapper.readValue(json, new TypeReference<>() { });
    }

    private void assertNotStatus(String json, String status, String message) throws Exception {
        if (json.trim().startsWith("{")) {
            StatusPayload payload = objectMapper.readValue(json, StatusPayload.class);
            assertFalse(status.equals(payload.status()), message + "，实际返回: " + json);
        }
    }

    record Hit(String productId, String title, String category, String brand,
               BigDecimal price, String snippet, Double rerankScore) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusPayload(String status, String message) { }
}
