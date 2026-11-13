package ai.lifo.agent.graph.translate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 把 ExpanderNode 聚合后的完整文本按换行拆成 List<String>。
 *
 * @author zhanglifeng
 * @since 2026-11-10
 */
@Slf4j
public class ExpanderProcessNode implements NodeAction {

    public static final String NODE_NAME = "expander_process";
    public static final String OUTPUT_KEY = "expander_content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object raw = state.value(ExpanderNode.STREAM_OUTPUT_KEY).orElse("");

        // 框架聚合后通常是 AssistantMessage，也可能是 String，做一次兼容
        String text = raw instanceof Message msg ? msg.getText() : String.valueOf(raw);

        List<String> queryVariants = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        log.info("expander process node produced {} variants", queryVariants.size());
        return Map.of(OUTPUT_KEY, queryVariants);
    }
}