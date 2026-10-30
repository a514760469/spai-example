package ai.lifo.agent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 读取邮件节点
 *
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public class ReadEmailNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 在生产环境中，这将连接到您的邮件服务
        String emailContent = state.value("email_content")
                .map(v -> (String) v)
                .orElse("");

        log.info("Processing email: {}", emailContent);

        List<String> messages = new ArrayList<>();
        messages.add("Processing email: " + emailContent);

        return Map.of("messages", messages);
    }

}
