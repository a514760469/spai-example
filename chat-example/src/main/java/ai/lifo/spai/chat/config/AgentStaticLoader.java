package ai.lifo.spai.chat.config;

import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.graph.agent.Agent;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * chat ui
 * @author zhanglifeng
 * @since 2026-09-18
 */
@Component
public class AgentStaticLoader implements AgentLoader {

    private Map<String, Agent> agents = new ConcurrentHashMap<>();

    public AgentStaticLoader() {
    }

    public AgentStaticLoader(Agent... agents) {
        this.agents = (Map) Arrays.stream(agents).collect(Collectors.toUnmodifiableMap(Agent::name, Function.identity()));
    }

    @Nonnull
    @Override
    public List<String> listAgents() {
        return this.agents.keySet().stream().toList();
    }

    @Override
    public Agent loadAgent(String name) {
        if (name != null && !name.trim().isEmpty()) {
            Agent agent = this.agents.get(name);
            if (agent == null) {
                throw new NoSuchElementException("Agent not found: " + name);
            } else {
                return agent;
            }
        } else {
            throw new IllegalArgumentException("Agent name cannot be null or empty");
        }
    }
}
