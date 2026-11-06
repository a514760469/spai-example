package ai.lifo.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * bug 跟踪节点
 * @author zhanglifeng
 * @since 2026-11-03
 */
@Slf4j
public class BugTrackingNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 在您的bug跟踪系统中创建票据
        String ticketId = "BUG-12345";  // 将通过API创建

        log.info("Created bug ticket: {}", ticketId);

        return Map.of(
                "search_results", List.of("已创建Bug票据 " + ticketId),
                "current_step", "bug_tracked",
                "next_node", "draft_response"
        );
    }
}
