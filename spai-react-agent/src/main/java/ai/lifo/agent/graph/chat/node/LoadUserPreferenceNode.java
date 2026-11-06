package ai.lifo.agent.graph.chat.node;

import ai.lifo.agent.config.UserPreferences;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.fastjson.JSONObject;
import jakarta.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 加载用户偏好node
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Slf4j
public class LoadUserPreferenceNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "loadUserPreference";

//    public static final String

//    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        return Map.of();
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        String userId = (String) state.value("userId").orElse("");

        if (userId.isEmpty()) {
            return Map.of(UserPreferences.KEY, UserPreferences.DEFAULT);
        }
        // 从 Store 加载用户偏好
        Store store = config.store();
        if (store != null) {
            Optional<StoreItem> itemOpt = store.getItem(List.of(UserPreferences.KEY), userId);
            if (itemOpt.isPresent()) {
                Map<String, Object> preferences = itemOpt.get().getValue();
                Object o = preferences.get(UserPreferences.KEY);
                String jsonString = JSONObject.toJSONString(o);
                UserPreferences userPreferences = JSONObject.parseObject(jsonString, UserPreferences.class);
                if (userPreferences != null) {
                    return Map.of(UserPreferences.KEY, userPreferences);
                }
            }
        }
        log.info("User preferences not found in store, using default preferences");
        // 如果未找到，返回默认偏好
        return Map.of(UserPreferences.KEY, UserPreferences.DEFAULT);
    }
}
