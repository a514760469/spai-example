package ai.lifo.agent.controller;

import com.alibaba.cloud.ai.agent.studio.dto.AgentRunRequest;
import com.alibaba.cloud.ai.agent.studio.dto.messages.UserMessageDTO;
import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author zhanglifeng
 * @since 2026-10-19
 */
@Slf4j
@RestController
@RequestMapping("/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final Store store;

    private final AgentLoader agentLoader;

    @GetMapping("/putStore")
    public String putStore() {
        // 向存储中写入示例数据
//        Map<String, Object> userData = new HashMap<>();
//        userData.put("name", "张三");
//        userData.put("language", "中文");
//        StoreItem storeItem = StoreItem.of(List.of("user"), "user_123", userData);
//        store.putItem(storeItem);

        // 模拟数据，预先填充用户画像
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("name", "王小明");
        profileData.put("age", 28);
        profileData.put("email", "wang@example.com");
        profileData.put("preferences", List.of("喜欢咖啡", "喜欢阅读"));

        StoreItem profileItem = StoreItem.of(List.of("user_profiles"), "user_001", profileData);
        store.putItem(profileItem);


        return "Store item put successfully";
    }

    @GetMapping("/run")
    public Flux<String> getMessage(String question, @RequestParam(defaultValue = "user_123") String userId) throws GraphRunnerException {
        AgentRunRequest request = new AgentRunRequest();
        request.threadId = UUID.randomUUID().toString();
        request.userId = userId;
        request.appName = "react-agent";
        request.newMessage = new UserMessageDTO(question);

        Agent agent = agentLoader.loadAgent(request.appName);
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(request.threadId)
                .addMetadata("user_id", request.userId)
                .store(store)
                .build();

        SystemMessage sysMessage = SystemMessage.builder()
                .text("需要查询用户信息时，调用 getUserInfo 工具，namespace传 ['user'], userId 由系统自动注入，你无需关心;" +
                        "需要保存用户信息时，调用 saveUserinfo 工具，namespace传 ['user']")
                .build();

        Flux<NodeOutput> stream = agent.stream(List.of(sysMessage, request.newMessage.toUserMessage()), runnableConfig);
        // Convert Flux<NodeOutput> to Flux<String>
        return stream.map(nodeOutput -> {
            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                Message msg = streamingOutput.message();
                if (msg == null) {
                    return "";
                }
//                log.info("Streaming output: {}", msg.getText());
                return msg.getText();
            }
            return "";
        });
    }


}
