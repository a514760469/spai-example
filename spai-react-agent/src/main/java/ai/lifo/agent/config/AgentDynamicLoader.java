package ai.lifo.agent.config;

import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.graph.agent.Agent;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * chat ui
 *
 * @author zhanglifeng
 * @since 2026-09-18
 */
@Component
public class AgentDynamicLoader implements AgentLoader {

    private final Map<String, Agent> agents;

    public AgentDynamicLoader(Agent... agents) {
        this.agents = Arrays.stream(agents).collect(Collectors.toUnmodifiableMap(Agent::name, Function.identity()));
    }

    @Nonnull
    @Override
    public List<String> listAgents() {
        return this.agents.keySet().stream().toList();
    }

    @Override
    public Agent loadAgent(String name) {
        return this.agents.getOrDefault(name, agents.get(listAgents().getFirst()));
    }
}
