package ai.lifo.spai.shopping.rag;

import ai.lifo.spai.shopping.tool.ProductRagSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R12 检索质量评估：语义链路（embedding 召回 + rerank 精排，即 productRagSearch 真实输出）
 * 对比关键词基线（复刻服务端 searchByQuery 的 LIKE '%q%' 语义，见 ProductRepository.java:20）。
 * <p>
 * 金标集基于 mockecomdb 真实商品人工标注（用 8 位 id 前缀书写，运行前经 REST 全量目录解析为完整 UUID）。
 * 指标：recall@5 = |top5 ∩ gold| / min(5,|gold|)；RR = 首个命中的名次倒数，未命中记 0。
 * <p>
 * 注意：每个用例真实调用一次 embedding + 一次 rerank，非 CI 常驻用例，按需手动运行。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@SpringBootTest
class RagRecallEvalTest {

    private record EvalCase(String query, List<String> goldIdPrefixes) {
    }

    /** 与产品侧完全一致的工具入口，评估"模型实际看到什么" */
    private record ToolHit(String productId, Double rerankScore) {
    }

    /** REST 目录行，只为基线和前缀解析服务 */
    private record CatalogRow(String id, String title, String category, String brand) {
    }

    private record ProductPageResp(int page, List<CatalogRow> products, int totalPages) {
    }

    private static final List<EvalCase> CASES = List.of(
            new EvalCase("买给爸妈的实用礼物，能平时关注身体健康",
                    List.of("ce191599", "7a28ff0f", "3f96b751", "d293c0fe", "aecba838", "7a6fcad0", "c6b74251", "82826846", "2efff754")),
            new EvalCase("长时间用电脑脖子酸，想热敷按摩一下颈椎",
                    List.of("d293c0fe", "7a6fcad0")),
            new EvalCase("预算五百块以内，坐地铁听歌想安静一点",
                    List.of("7d759ae9", "a67462e5", "c93b8644")),
            new EvalCase("老人一个人在家，想远程帮他留意血压",
                    List.of("2079a308", "7a28ff0f", "aecba838")),
            new EvalCase("程序员写代码用的键盘，能自己改按键映射",
                    List.of("52acb7ed")),
            new EvalCase("岳父爱喝茶，过节拎什么有面子",
                    List.of("cfb944bb", "53de3ce2", "9e4966f4", "c678ca55")),
            new EvalCase("给爸妈买个平板追剧，屏幕看着不累眼",
                    List.of("cabe534f", "ae094234")),
            new EvalCase("打电竞要求按键触发越快越好",
                    List.of("c310cd37", "cf18873c", "e2395b23")),
            new EvalCase("晚上拍人像清晰、长焦强的手机",
                    List.of("8d5f629b", "134352a6", "733b2c96", "741111ae", "ae8feb38")),
            new EvalCase("六十块以内的备用耳机，日常通勤听听歌",
                    List.of("f7e92720")),
            new EvalCase("打豆浆榨果汁都行，最好做完不用手洗",
                    List.of("86435139", "67273620", "2efff754")),
            new EvalCase("出差多，想要特别轻的商务笔记本",
                    List.of("7c3b4c19", "e48bd92b", "a857215c")),
            new EvalCase("客厅用的柜式空调，夏天开得住又省电",
                    List.of("a9a6ec1a")),
            new EvalCase("一家人的衣服一次洗完，晚上洗不能吵到邻居",
                    List.of("1bb829d4", "4a9934ca")),
            new EvalCase("数据线老是断，想买根结实耐用的",
                    List.of("59945b9e", "b9c8a51a", "19bbb6d0", "d50f61e0")));

    private static List<CatalogRow> catalog;

    @Value("${product.base-url}")
    private String productBaseUrl;

    @Autowired
    private ProductRagSearchTool ragSearchTool;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void loadCatalog(@Autowired RestClient.Builder builder,
                            @Value("${product.base-url}") String baseUrl) {
        RestClient client = builder.baseUrl(baseUrl).build();
        catalog = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            ProductPageResp resp = client.get()
                    .uri("/api/v1/products?page={p}&size=50", page)
                    .retrieve()
                    .body(ProductPageResp.class);
            assertFalse(resp == null || resp.products() == null, "REST 目录拉取失败: page=" + page);
            catalog.addAll(resp.products());
            totalPages = resp.totalPages();
            page++;
        } while (page < totalPages);
        log.info("评估目录加载完成: {} 条", catalog.size());
    }

    @Test
    void eval_semantic_vs_keyword_baseline() throws Exception {
        assertTrue(catalog.size() > 400, "目录应覆盖全量商品，实际: " + catalog.size());

        log.info(String.format("%-28s %8s %8s %8s %8s", "query", "语义R@5", "语义RR", "关键词R@5", "关键词RR"));
        double semRecallSum = 0, semRrSum = 0, kwRecallSum = 0, kwRrSum = 0;
        List<String> semanticLosses = new ArrayList<>();

        for (EvalCase c : CASES) {
            Set<String> gold = resolveGold(c);
            assertFalse(gold.isEmpty(), "金标未匹配到任何在架商品: " + c.query());

            List<String> semIds = semanticTop5(c.query());
            List<String> kwIds = keywordTop5(c.query());

            double semR = recallAt5(semIds, gold);
            double semM = reciprocalRank(semIds, gold);
            double kwR = recallAt5(kwIds, gold);
            double kwM = reciprocalRank(kwIds, gold);
            semRecallSum += semR;
            semRrSum += semM;
            kwRecallSum += kwR;
            kwRrSum += kwM;
            if (semR == 0) {
                semanticLosses.add(c.query());
            }
            log.info(String.format("%-28s %8.2f %8.2f %8.2f %8.2f", c.query(), semR, semM, kwR, kwM));
            log.info("   语义top5: {}", semIds.stream().map(this::shortLabel).toList());
            if (kwIds.isEmpty()) {
                log.info("   关键词基线: 0 命中（整串 LIKE 匹配不到任何 title/category/brand）");
            } else {
                log.info("   关键词top5: {}", kwIds.stream().map(this::shortLabel).toList());
            }
        }

        int n = CASES.size();
        log.info("===== R12 汇总 ({} 个金标查询) =====", n);
        log.info("语义链路   recall@5={} MRR={}", fmt(semRecallSum / n), fmt(semRrSum / n));
        log.info("关键词基线 recall@5={} MRR={}", fmt(kwRecallSum / n), fmt(kwRrSum / n));
        if (!semanticLosses.isEmpty()) {
            log.info("语义链路零命中查询: {}", semanticLosses);
        }

        assertTrue(semRecallSum > kwRecallSum,
                "语义链路 recall@5 总量应超过关键词基线（否则 RAG 立项不成立）");
        assertTrue(semRrSum > 0, "语义链路 MRR 不应为 0");
    }

    private List<String> semanticTop5(String query) throws Exception {
        String json = ragSearchTool.productRagSearch(query, null, null);
        if (json.startsWith("{")) {
            Map<?, ?> status = objectMapper.readValue(json, Map.class);
            log.warn("语义链路未走通 query={} status={}", query, status.get("status"));
            return List.of();
        }
        List<ToolHit> hits = objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, ToolHit.class));
        return hits.stream().map(ToolHit::productId).limit(5).toList();
    }

    /** 复刻 ProductRepository.searchByQuery：整串 query 对小写 title/category/brand 做 LIKE %q%，保持仓库顺序 */
    private List<String> keywordTop5(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return catalog.stream()
                .filter(row -> (row.title() != null && row.title().toLowerCase(Locale.ROOT).contains(q))
                        || (row.category() != null && row.category().toLowerCase(Locale.ROOT).contains(q))
                        || (row.brand() != null && row.brand().toLowerCase(Locale.ROOT).contains(q)))
                .limit(5)
                .map(CatalogRow::id)
                .toList();
    }

    private Set<String> resolveGold(EvalCase c) {
        Set<String> gold = new LinkedHashSet<>();
        for (String prefix : c.goldIdPrefixes()) {
            catalog.stream().filter(row -> row.id().startsWith(prefix)).findFirst()
                    .ifPresent(row -> gold.add(row.id()));
        }
        return gold;
    }

    private double recallAt5(List<String> top5, Set<String> gold) {
        if (top5.isEmpty()) {
            return 0;
        }
        long hits = top5.stream().filter(gold::contains).count();
        return (double) hits / Math.min(5, gold.size());
    }

    private double reciprocalRank(List<String> ranked, Set<String> gold) {
        for (int i = 0; i < ranked.size(); i++) {
            if (gold.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    private String shortLabel(String id) {
        return catalog.stream().filter(r -> r.id().equals(id)).findFirst()
                .map(r -> id.substring(0, 8) + ":" + r.title())
                .orElse(id);
    }

    private String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
