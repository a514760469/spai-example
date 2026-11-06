package ai.lifo.agent.graph.node;

import ai.lifo.agent.graph.EmailClassification;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 人工审核节点
 * 注意：在 interruptBefore 模式下，中断是在编译配置中设置的（见 createEmailAgentGraph 方法）。
 * 节点本身不需要做任何特殊处理，只需要正常返回状态即可。
 * 当执行到此节点前时，Graph 会自动中断，等待人工输入。
 * @author zhanglifeng
 * @since 2026-11-03
 */
@Slf4j
public class HumanReviewNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        EmailClassification classification = state.value("classification")
                .map(v -> (EmailClassification) v)
                .orElse(new EmailClassification());

        // 准备审核数据
        Map<String, Object> reviewData = Map.of(
                "email_id", state.value("email_id").map(v -> (String) v).orElse(""),
                "original_email", state.value("email_content").map(v -> (String) v).orElse(""),
                "draft_response", state.value("draft_response").map(v -> (String) v).orElse(""),
                "urgency", classification.getUrgency(),
                "intent", classification.getIntent(),
                "action", "请审核并批准/编辑此响应"
        );

        log.info("Waiting for human review: {}", reviewData);


        // 返回审核数据和下一个节点
        // 注意：在 interruptBefore 模式下，此节点在人工输入后才会执行
        return Map.of(
                "review_data", reviewData,
                "status", "waiting_for_review",
                "next_node", "send_reply"
        );
    }
}
