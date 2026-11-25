package ai.lifo.spai.shopping.memory;

import ai.lifo.spai.shopping.mapper.UserMemory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 长期记忆服务：MySQL 权威存储 + Redis 向量副本。
 * <p>
 * 去重策略：同一用户 content 完全一致由唯一键拦截（语义相近的合并交给抽取 prompt 约束措辞）。
 * 删除以 MySQL 为准，向量副本同步删，测试与用户注销走 deleteByUser。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@Service
public class MemoryService {

    private static final int PROFILE_LIMIT = 10;

    private static final long PROFILE_CACHE_TTL_MILLIS = 60_000;

    private record CachedProfile(String text, long timestamp) {
    }

    private final UserMemoryStore store;

    private final VectorStore memoryVectorStore;

    private final MeterRegistry meterRegistry;

    private final Map<String, CachedProfile> profileCache = new ConcurrentHashMap<>();

    public MemoryService(UserMemoryStore store,
                         @Qualifier("memoryVectorStore") VectorStore memoryVectorStore,
                         MeterRegistry meterRegistry) {
        this.store = store;
        this.memoryVectorStore = memoryVectorStore;
        this.meterRegistry = meterRegistry;
    }

    public record MemoryItem(String memoryType, String content) {
    }

    public record MemoryHit(String memoryType, String content, Double score) {
    }

    /**
     * @return 实际新增条数（重复记忆被唯一键拦截，不计入）
     */
    public int write(String userKey, String sourceThread, List<MemoryItem> items) {
        int saved = 0;
        for (MemoryItem item : items) {
            if (item.content() == null || item.content().isBlank()) {
                continue;
            }
            long id = store.insert(userKey, item.memoryType(), item.content().trim(), sourceThread);
            if (id < 0) {
                meterRegistry.counter("shopping.memory.events", "event", "duplicate").increment();
                continue;
            }
            memoryVectorStore.add(List.of(Document.builder()
                    .id(String.valueOf(id))
                    .text(item.content().trim())
                    .metadata(Map.of("userKey", userKey, "memoryType", item.memoryType()))
                    .build()));
            saved++;
            meterRegistry.counter("shopping.memory.events", "event", "saved",
                    "type", item.memoryType()).increment();
        }
        if (saved > 0) {
            profileCache.remove(userKey);
            log.info("用户记忆写入: user={}, +{} 条", maskUser(userKey), saved);
        }
        return saved;
    }

    public List<MemoryHit> recall(String userKey, String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("userKey", userKey).build())
                .build();
        List<Document> docs = memoryVectorStore.similaritySearch(request);
        if (docs.isEmpty()) {
            return List.of();
        }
        List<MemoryHit> hits = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            Object type = doc.getMetadata().get("memoryType");
            hits.add(new MemoryHit(type == null ? "FACT" : type.toString(),
                    doc.getText() == null ? "" : doc.getText(), doc.getScore()));
        }
        return hits;
    }

    /** 常驻注入用的画像摘要；无记忆返回空串，调用方零开销跳过 */
    public String profile(String userKey) {
        CachedProfile cached = profileCache.get(userKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp() < PROFILE_CACHE_TTL_MILLIS) {
            return cached.text();
        }
        List<UserMemory> rows = store.listByUser(userKey, PROFILE_LIMIT);
        String text = rows.isEmpty() ? ""
                : rows.stream()
                        .map(row -> "- " + row.getContent())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
        profileCache.put(userKey, new CachedProfile(text, now));
        return text;
    }

    public int deleteByUser(String userKey) {
        List<Long> ids = store.deleteByUser(userKey);
        if (!ids.isEmpty()) {
            memoryVectorStore.delete(ids.stream().map(String::valueOf).toList());
        }
        profileCache.remove(userKey);
        return ids.size();
    }

    private String maskUser(String userKey) {
        return userKey.length() <= 4 ? "****" : userKey.substring(0, 3) + "****" + userKey.substring(userKey.length() - 2);
    }
}