package ai.lifo.agent.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

/**
 * 创建一个工具，让Agent能够查询用户信息
 * @author zhanglifeng
 * @since 2026-10-16
 */
public class UserInfoTool implements BiFunction<UserInfoTool.GetMemoryRequest, ToolContext, UserInfoTool.MemoryResponse> {

    public static ToolCallback getUserInfoFunction() {
        return FunctionToolCallback.builder("getUserInfo", new UserInfoTool())
                .description("查询用户信息")
                .inputType(GetMemoryRequest.class)
                .build();
    }


    @Override
    public MemoryResponse apply(GetMemoryRequest request, ToolContext context) {
        RunnableConfig runnableConfig = (RunnableConfig) context.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
        Store store = runnableConfig.store();
        Optional<StoreItem> itemOpt = store.getItem(request.namespace(), request.key());
        if (itemOpt.isPresent()) {
            Map<String, Object> value = itemOpt.get().getValue();
            return new MemoryResponse("找到用户信息", value);
        }
        return new MemoryResponse("未找到用户", Map.of());
    }

    /**
     * 请求和响应记录
     */
    public record GetMemoryRequest(List<String> namespace, String key) { }
    public record MemoryResponse(String message, Map<String, Object> value) { }
}
