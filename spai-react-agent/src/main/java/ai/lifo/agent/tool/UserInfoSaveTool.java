package ai.lifo.agent.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

/**
 * 创建保存用户信息的工具
 *
 * @author zhanglifeng
 * @since 2026-10-20
 */
public class UserInfoSaveTool implements BiFunction<UserInfoSaveTool.SaveMemoryRequest, ToolContext, UserInfoSaveTool.MemoryResponse> {

    public static ToolCallback saveUserInfoFunction() {
        return FunctionToolCallback.builder("saveUserInfo", new UserInfoSaveTool())
                .description("保存用户信息")
                .inputType(SaveMemoryRequest.class)
                .build();
    }

    @Override
    public MemoryResponse apply(SaveMemoryRequest request, ToolContext context) {
        RunnableConfig runnableConfig = (RunnableConfig) context.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
        Store store = runnableConfig.store();

        // 优先从 metadata 中取真实 user_id，取不到再回退到 AI 传入的参数
        String userId = runnableConfig.metadata("user_id")
                .map(Object::toString)
                .orElse(request.userId());

        StoreItem item = StoreItem.of(request.namespace(), userId, request.value());
        store.putItem(item);
        return new MemoryResponse("成功保存用户信息", request.value());
    }

    // 定义请求记录
    public record SaveMemoryRequest(List<String> namespace, String userId, Map<String, Object> value) { }

    public record MemoryResponse(String message, Map<String, Object> value) { }
}
