package ai.lifo.agent.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

/**
 * 邮件分类结构
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Data
@NoArgsConstructor
public class EmailClassification {
    private String intent;      // "question", "bug", "billing", "feature", "complex"
    private String urgency;     // "low", "medium", "high", "critical"
    private String topic;
    private String summary;

    public EmailClassification(String intent, String urgency, String topic, String summary) {
        this.intent = intent;
        this.urgency = urgency;
        this.topic = topic;
        this.summary = summary;
    }

    // 配置状态键策略
    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("email_content", new ReplaceStrategy());
            strategies.put("sender_email", new ReplaceStrategy());
            strategies.put("email_id", new ReplaceStrategy());
            strategies.put("classification", new ReplaceStrategy());
            strategies.put("search_results", new ReplaceStrategy());
            strategies.put("customer_history", new ReplaceStrategy());
            strategies.put("draft_response", new ReplaceStrategy());
            strategies.put("messages", new AppendStrategy());
            strategies.put("next_node", new ReplaceStrategy());
            strategies.put("status", new ReplaceStrategy());
            strategies.put("review_data", new ReplaceStrategy());
            return strategies;
        };
    }
}
