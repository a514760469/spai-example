package ai.lifo.spai.shopping.mapper;

import lombok.Data;

/**
 * GRAPH_THREAD 父线程摘要投影。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Data
public class GraphThreadSummary {

    private String threadName;

    private long lastActive;
}
