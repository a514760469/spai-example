package ai.lifo.spai.shopping.rag;

import java.time.Instant;

/**
 * @author zhanglifeng
 * @since 2026-11-23
 */
public interface ProductIndexService {

    /**
     * 全量重建：分页拉取 server REST → 组装文档 → embedding → 写入 Redis 索引。返回索引条数
     */
    int reindex();

    IndexStats stats();


    /** 当前索引状态：条数、最后重建时间— */
    record IndexStats(long documentCount, Instant lastRebuiltAt, String indexName) { }
}
