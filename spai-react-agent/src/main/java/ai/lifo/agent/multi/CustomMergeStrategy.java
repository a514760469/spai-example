package ai.lifo.agent.multi;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;

import java.util.Map;

/**
 * 自定义合并策略
 * 实现自定义的合并策略来控制如何组合多个Agent的输出
 *
 * @author zhanglifeng
 * @since 2026-10-20
 */
public class CustomMergeStrategy implements ParallelAgent.MergeStrategy {


    @Override
    public Object merge(Map<String, Object> mergedState, OverAllState state) {
        // 从每个Agent的状态中提取输出
        state.data().forEach((key, value) -> {
            // 检查key不为null且以"_result"结尾
            if (key != null && key.endsWith("_result")) {
                String resultText = "";
                if (value instanceof GraphResponse<?> graphResponse) {
                    if (graphResponse.resultValue().isPresent()) {
                        resultText = graphResponse.resultValue().get().toString();
                    }
                } else if (value != null) {
                    resultText = value.toString();
                }
                Object existing = mergedState.get("merged_results");
                if (existing == null) {
                    mergedState.put("merged_results", resultText);
                }
                else {
                    mergedState.put("merged_results", existing + "\n\n---\n\n" + resultText);
                }
            }
        });
        return mergedState;
    }
}
