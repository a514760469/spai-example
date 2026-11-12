package ai.lifo.agent.graph.email;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 发送回复节点
 * @author zhanglifeng
 * @since 2026-11-03
 */
@Slf4j
public class SendReplyNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String draftResponse = state.value("draft_response")
                .map(v -> (String) v)
                .orElse("");

        // 与邮件服务集成
        log.info("Sending reply: {}...",
                draftResponse.length() > 100
                        ? draftResponse.substring(0, 100)
                        : draftResponse);

        return Map.of("status", "sent");
    }
}
