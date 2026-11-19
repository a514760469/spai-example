package ai.lifo.spai.shopping.store;

import com.alibaba.cloud.ai.graph.store.NamespaceListRequest;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.StoreSearchRequest;
import com.alibaba.cloud.ai.graph.store.StoreSearchResult;
import com.alibaba.cloud.ai.graph.store.stores.BaseStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author zhanglifeng
 * @since 2026-11-18
 */
public class MysqlStore extends BaseStore {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public MysqlStore(DataSource dataSource, JdbcTemplate jdbc) {
        this(dataSource, jdbc, "spring_ai_store");
        initializeTable();
    }

    /**
     * Constructor with custom table name.
     * @param dataSource database data source
     * @param tableName table name
     */
    public MysqlStore(DataSource dataSource, JdbcTemplate jdbc, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        initializeTable();
    }

    @Override
    public void putItem(StoreItem item) {
        validatePutItem(item);

        String itemId = createItemId(item.getNamespace(), item.getKey());
        try {
            String namespaceJson = objectMapper.writeValueAsString(item.getNamespace());
            String valueJson = objectMapper.writeValueAsString(item.getValue());

            String sql = """
                INSERT INTO %s (id, namespace, key_name, value_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    namespace  = VALUES(namespace),
                    key_name   = VALUES(key_name),
                    value_json = VALUES(value_json),
                    updated_at = VALUES(updated_at)
                """.formatted(tableName);

            jdbc.update(sql, itemId, namespaceJson, item.getKey(), valueJson,
                    new Timestamp(item.getCreatedAt()), new Timestamp(item.getUpdatedAt()));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private String createItemId(List<String> namespace, String key) {
        return createStoreKey(namespace, key);
    }

    @Override
    public Optional<StoreItem> getItem(List<String> namespace, String key) {
        validateGetItem(namespace, key);
        String sql = """
                SELECT namespace, key_name, value_json, created_at, updated_at FROM %s WHERE id = ?
                """.formatted(tableName);
        String itemId = createItemId(namespace, key);
        List<StoreItem> items = jdbc.query(sql, (rs, rowNum) -> mapRow(rs), itemId);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    @Override
    public boolean deleteItem(List<String> namespace, String key) {
        validateDeleteItem(namespace, key);
        String sql = """
                DELETE FROM %s WHERE id = ?
                """.formatted(tableName);
        int update = jdbc.update(sql, createItemId(namespace, key));
        return update > 0;
    }

    @Override
    public StoreSearchResult searchItems(StoreSearchRequest searchRequest) {
        validateSearchItems(searchRequest);

        List<StoreItem> filteredItems = getAllItems().stream()
                .filter(item -> matchesSearchCriteria(item, searchRequest))
                .collect(Collectors.toList());

        if (!searchRequest.getSortFields().isEmpty()) {
            filteredItems.sort(createComparator(searchRequest));
        }

        long totalCount = filteredItems.size();
        int offset = searchRequest.getOffset();
        int limit = searchRequest.getLimit();

        if (offset >= filteredItems.size()) {
            return StoreSearchResult.of(Collections.emptyList(), totalCount, offset, limit);
        }
        int endIndex = Math.min(offset + limit, filteredItems.size());
        return StoreSearchResult.of(filteredItems.subList(offset, endIndex), totalCount, offset, limit);
    }

    @Override
    public List<String> listNamespaces(NamespaceListRequest namespaceRequest) {
        validateListNamespaces(namespaceRequest);

        List<String> prefixFilter = namespaceRequest.getNamespace();
        Set<String> namespaceSet = new HashSet<>();

        for (StoreItem item : getAllItems()) {
            List<String> itemNamespace = item.getNamespace();
            if (!prefixFilter.isEmpty() && !startsWithPrefix(itemNamespace, prefixFilter)) {
                continue;
            }
            // 逐层展开 namespace 路径，maxDepth=-1 表示全深度
            int maxDepth = namespaceRequest.getMaxDepth();
            int depth = (maxDepth == -1) ? itemNamespace.size() : Math.min(maxDepth, itemNamespace.size());
            for (int i = 1; i <= depth; i++) {
                namespaceSet.add(String.join("/", itemNamespace.subList(0, i)));
            }
        }

        List<String> namespaces = new ArrayList<>(namespaceSet);
        Collections.sort(namespaces);

        int offset = namespaceRequest.getOffset();
        int limit = namespaceRequest.getLimit();
        if (offset >= namespaces.size()) {
            return Collections.emptyList();
        }
        return namespaces.subList(offset, Math.min(offset + limit, namespaces.size()));
    }

    private List<StoreItem> getAllItems() {
        String sql = """
                SELECT namespace, key_name, value_json, created_at, updated_at FROM %s
                """.formatted(tableName);
        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    private StoreItem mapRow(ResultSet rs) throws SQLException {
        try {
            List<String> namespace = objectMapper.readValue(
                    rs.getString("namespace"), new TypeReference<List<String>>() {});
            Map<String, Object> value = objectMapper.readValue(
                    rs.getString("value_json"), new TypeReference<Map<String, Object>>() {});
            return new StoreItem(
                    namespace,
                    rs.getString("key_name"),
                    value,
                    rs.getTimestamp("created_at").getTime(),
                    rs.getTimestamp("updated_at").getTime());
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear() {
        String sql = "DELETE FROM " + tableName;

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to clear database store", e);
        }
    }

    @Override
    public long size() {
        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            rs.next();
            return rs.getLong(1);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to get store size from database", e);
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }


    /**
     * Initialize database table.
     */
    private void initializeTable() {
        // Create table with database-agnostic SQL
        String sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        id         VARCHAR(500) PRIMARY KEY,
                        namespace  TEXT,
                        key_name   VARCHAR(500),
                        value_json TEXT,
                        created_at DATETIME(3),
                        updated_at DATETIME(3)
                    ) DEFAULT CHARSET=utf8mb4
                """.formatted(tableName);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to initialize table", e);
        }
    }
}
