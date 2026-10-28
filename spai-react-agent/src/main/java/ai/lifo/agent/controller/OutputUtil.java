package ai.lifo.agent.controller;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhanglifeng
 * @since 2026-10-20
 */
@Slf4j
public class OutputUtil {

    public static final Map<String, Boolean> thinkingStartMap = new ConcurrentHashMap<>();

    public static String obtainThinkingOutputStreaming(StreamingOutput<?> streamingOutput) {

        OutputType outputType = streamingOutput.getOutputType();
        if (OutputType.AGENT_MODEL_STREAMING.equals(outputType)) {
            Message message = streamingOutput.message();
            String id = message.getMetadata().get("id").toString();
            if (id == null) {
                log.warn("模型输出？ id is null");
                return "";
            }
            // 插入start
            Boolean start = thinkingStartMap.get(id);
//            Boolean start = thinkingStartMap.computeIfAbsent(id, k -> false);

            String thinkingContent = (String) message.getMetadata().get("reasoningContent");
            if (StringUtils.hasText(thinkingContent)) {
                // 输出思考内容
//                log.info("思考内容：{}", thinkingContent);
                // 刚开始出现思考数据
                if (start == null || !start) {
                    thinkingStartMap.put(id, true);
                    return "[thinking]" + thinkingContent;
                }
                // 后续内容直接输出
                return thinkingContent;
            }
            // thinking 结束开始输出内容
            String text = message.getText();
            // 判断是否是 thinking 刚结束
            if (StringUtils.hasText(text) && thinkingStartMap.remove(id, true)) {
                return "[/thinking]" + text;
            }
            return text == null ? "" : text;
        }
        return "";
    }

    /**
     * 打印工作流图表
     * 使用PlantUML格式展示Agent工作流的结构
     */
    public static void printGraphRepresentation(Agent agent) {
        System.out.println("\n=== 工作流图表 ===");
        GraphRepresentation representation = agent.getAndCompileGraph().getGraph(GraphRepresentation.Type.PLANTUML);
        System.out.println(representation.content());
        System.out.println("=========================\n");
    }
}
