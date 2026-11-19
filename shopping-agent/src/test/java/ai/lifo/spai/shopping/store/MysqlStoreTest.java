package ai.lifo.spai.shopping.store;

import com.alibaba.cloud.ai.graph.store.NamespaceListRequest;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.StoreSearchRequest;
import com.alibaba.cloud.ai.graph.store.StoreSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MysqlStore 集成验证：@SpringBootTest 走 application.yaml 的真实数据源（MySQL 5.7），
 * 数据直接落在 spring_ai_store 表并保留，便于事后查看。
 * 每次运行用唯一 namespace 根（storetest/{runId}）隔离，各测试再各占一个子空间，
 * 因此重复运行互不干扰，也不依赖执行顺序。
 * clear() 会清空整表，为不破坏保留数据，在独立 scratch 表上验证。
 */
@SpringBootTest
class MysqlStoreTest {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MysqlStore store;

    @BeforeEach
    void initStore() {
        store = new MysqlStore(dataSource, jdbcTemplate);
    }

    private static List<String> ns(String... parts) {
        List<String> namespace = new ArrayList<>(List.of("storetest", RUN_ID));
        namespace.addAll(List.of(parts));
        return namespace;
    }

    private static String path(String... parts) {
        return String.join("/", ns(parts));
    }

    @Test
    void putAndGetRoundTrip() {
        List<String> namespace = ns("rt", "user", "u1");
        StoreItem item = StoreItem.of(namespace, "budget", Map.of("amount", 500, "currency", "CNY", "备注", "稻香村"));
        store.putItem(item);

        StoreItem got = store.getItem(namespace, "budget").orElseThrow();
        assertEquals(namespace, got.getNamespace());
        assertEquals("budget", got.getKey());
        assertEquals(500, got.getValue().get("amount"));
        assertEquals("CNY", got.getValue().get("currency"));
        assertEquals("稻香村", got.getValue().get("备注"));
        assertEquals(item.getCreatedAt(), got.getCreatedAt());
        assertEquals(item.getUpdatedAt(), got.getUpdatedAt());
    }

    @Test
    void upsertIsIdempotentAndPreservesCreatedAt() {
        List<String> namespace = ns("up", "user", "u1");
        long created = 1_700_000_000_123L;
        store.putItem(new StoreItem(namespace, "budget", Map.of("amount", 500), created, created));
        store.putItem(new StoreItem(namespace, "budget", Map.of("amount", 800), created + 120_000, created + 60_000));

        StoreSearchResult scoped = store.searchItems(StoreSearchRequest.builder().namespace(namespace.toArray(String[]::new)).build());
        assertEquals(1, scoped.getTotalCount());

        StoreItem got = store.getItem(namespace, "budget").orElseThrow();
        assertEquals(800, got.getValue().get("amount"));
        assertEquals(created, got.getCreatedAt());
        assertEquals(created + 60_000, got.getUpdatedAt());
    }

    @Test
    void getMissingReturnsEmpty() {
        assertTrue(store.getItem(ns("missing"), "nonexistent").isEmpty());
    }

    @Test
    void deleteItemReturnsTrueThenFalse() {
        List<String> namespace = ns("del", "user", "u1");
        store.putItem(StoreItem.of(namespace, "budget", Map.of("amount", 500)));
        assertTrue(store.deleteItem(namespace, "budget"));
        assertFalse(store.deleteItem(namespace, "budget"));
        assertTrue(store.getItem(namespace, "budget").isEmpty());
    }

    @Test
    void searchByNamespacePrefix() {
        store.putItem(StoreItem.of(ns("ps", "user", "u1"), "budget", Map.of("amount", 500)));
        store.putItem(StoreItem.of(ns("ps", "user", "u1"), "brand", Map.of("name", "稻香村")));
        store.putItem(StoreItem.of(ns("ps", "user", "u2"), "budget", Map.of("amount", 300)));
        store.putItem(StoreItem.of(ns("ps", "order", "o1"), "status", Map.of("state", "PAID")));

        StoreSearchResult result = store.searchItems(StoreSearchRequest.builder()
                .namespace("storetest", RUN_ID, "ps", "user", "u1")
                .build());

        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().stream().allMatch(i -> i.getNamespace().equals(ns("ps", "user", "u1"))));
    }

    @Test
    void searchByFilterAndQuery() {
        store.putItem(StoreItem.of(ns("fq", "user", "u1"), "budget", Map.of("amount", 500, "currency", "CNY")));
        store.putItem(StoreItem.of(ns("fq", "user", "u1"), "allowance", Map.of("amount", 300, "currency", "USD")));

        StoreSearchResult byFilter = store.searchItems(StoreSearchRequest.builder()
                .namespace("storetest", RUN_ID, "fq")
                .filter(Map.of("currency", "CNY"))
                .build());
        assertEquals(1, byFilter.getTotalCount());
        assertEquals("budget", byFilter.getItems().get(0).getKey());

        StoreSearchResult byQuery = store.searchItems(StoreSearchRequest.builder()
                .namespace("storetest", RUN_ID, "fq")
                .query("BUDG")
                .build());
        assertEquals(1, byQuery.getTotalCount());
        assertEquals("budget", byQuery.getItems().get(0).getKey());
    }

    @Test
    void searchSortAndPagination() {
        store.putItem(StoreItem.of(ns("sort"), "a", Map.of("score", 30)));
        store.putItem(StoreItem.of(ns("sort"), "b", Map.of("score", 10)));
        store.putItem(StoreItem.of(ns("sort"), "c", Map.of("score", 20)));

        StoreSearchResult sorted = store.searchItems(StoreSearchRequest.builder()
                .namespace("storetest", RUN_ID, "sort")
                .sortFields(List.of("score"))
                .ascending(true)
                .build());
        assertEquals(List.of("b", "c", "a"), sorted.getItems().stream().map(StoreItem::getKey).toList());

        StoreSearchResult page = store.searchItems(StoreSearchRequest.builder()
                .namespace("storetest", RUN_ID, "sort")
                .sortFields(List.of("score"))
                .ascending(true)
                .offset(1)
                .limit(1)
                .build());
        assertEquals(3, page.getTotalCount());
        assertEquals(List.of("c"), page.getItems().stream().map(StoreItem::getKey).toList());

        StoreSearchResult beyond = store.searchItems(StoreSearchRequest.builder()
                .namespace("storetest", RUN_ID, "sort")
                .offset(10)
                .build());
        assertEquals(3, beyond.getTotalCount());
        assertTrue(beyond.getItems().isEmpty());
    }

    @Test
    void listNamespacesDepthPrefixAndPagination() {
        store.putItem(StoreItem.of(ns("nstest", "user", "u1", "prefs"), "k1", Map.of("v", 1)));
        store.putItem(StoreItem.of(ns("nstest", "user", "u2", "prefs"), "k2", Map.of("v", 2)));
        store.putItem(StoreItem.of(ns("nstest", "order", "o1"), "k3", Map.of("v", 3)));

        List<String> all = store.listNamespaces(NamespaceListRequest.builder()
                .namespace("storetest", RUN_ID, "nstest")
                .maxDepth(-1)
                .build());
        assertEquals(List.of(
                "storetest",
                "storetest/" + RUN_ID,
                path("nstest"),
                path("nstest", "order"),
                path("nstest", "order", "o1"),
                path("nstest", "user"),
                path("nstest", "user", "u1"),
                path("nstest", "user", "u1", "prefs"),
                path("nstest", "user", "u2"),
                path("nstest", "user", "u2", "prefs")), all);

        List<String> shallow = store.listNamespaces(NamespaceListRequest.builder()
                .namespace("storetest", RUN_ID, "nstest")
                .maxDepth(4)
                .build());
        assertEquals(List.of(
                "storetest",
                "storetest/" + RUN_ID,
                path("nstest"),
                path("nstest", "order"),
                path("nstest", "user")), shallow);

        List<String> page = store.listNamespaces(NamespaceListRequest.builder()
                .namespace("storetest", RUN_ID, "nstest")
                .maxDepth(-1)
                .offset(2)
                .limit(2)
                .build());
        assertEquals(List.of(path("nstest"), path("nstest", "order")), page);
    }

    @Test
    void sizeReflectsPutsAndDataPersists() {
        long before = store.size();
        assertFalse(store.isEmpty());
        store.putItem(StoreItem.of(ns("size"), "k1", Map.of("v", 1)));
        store.putItem(StoreItem.of(ns("size"), "k2", Map.of("v", 2)));
        assertEquals(before + 2, store.size());

        MysqlStore reopened = new MysqlStore(dataSource, jdbcTemplate);
        assertEquals(1, reopened.getItem(ns("size"), "k1").orElseThrow().getValue().get("v"));
    }

    @Test
    void clearEmptiesWholeTable() {
        MysqlStore scratch = new MysqlStore(dataSource, jdbcTemplate, "spring_ai_store_test");
        scratch.clear();
        assertTrue(scratch.isEmpty());

        scratch.putItem(StoreItem.of(ns("clear"), "k1", Map.of("v", 1)));
        scratch.putItem(StoreItem.of(ns("clear"), "k2", Map.of("v", 2)));
        assertEquals(2, scratch.size());
        assertFalse(scratch.isEmpty());

        scratch.clear();
        assertEquals(0, scratch.size());
        assertTrue(scratch.isEmpty());
    }
}