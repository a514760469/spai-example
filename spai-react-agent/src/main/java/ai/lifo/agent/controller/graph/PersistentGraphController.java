package ai.lifo.agent.controller.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * @author zhanglifeng
 * @since 2026-11-09
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PersistentGraphController {

    private final CompiledGraph chatGraph;

    private final RedisSaver redisSaver;

    /**
     * 获取所有历史状态
     */
    @GetMapping("/history/{threadId}")
    public Collection<StateSnapshot> history(@PathVariable String threadId) {

        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        Collection<StateSnapshot> stateHistory = chatGraph.getStateHistory(config);
        for (StateSnapshot stateSnapshot : stateHistory) {
            stateSnapshot.config().checkPointId().ifPresent(System.out::println);
            System.out.println("Node: " + stateSnapshot.node());
            System.out.println("Next: " + stateSnapshot.next());
            System.out.println("Config: " + stateSnapshot.config());
            System.out.println("State: " + stateSnapshot.state());
        }
        return stateHistory;
    }

    @DeleteMapping("/thread/{threadId}")
    public String deleteThread(@PathVariable String threadId) throws Exception {
        redisSaver.release(RunnableConfig.builder().threadId(threadId).build());
        return "OK";
    }

    @DeleteMapping("/checkpoint/{checkpointId}")
    public String deleteCheckpoint(@PathVariable String checkpointId) throws Exception {
        redisSaver.release(RunnableConfig.builder().checkPointId(checkpointId).build());
        return "OK";
    }

}
