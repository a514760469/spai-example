package ai.lifo.agent.config;

import ai.lifo.agent.tool.OpenMeteoService;
import ai.lifo.agent.tool.TimeService;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author zhanglifeng
 * @since 2026-10-10
 */
@Configuration
public class AgentConfiguration {



    @Component
    static class ExcludeBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor {

        // 使用自己的executionController，需要排除默认的 executionController
        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            registry.removeBeanDefinition("executionController");
        }
    }

    @Bean
    public ReactAgent reactAgent(ChatModel chatModel) {
        // 创建 hook
        Hook humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("getWeatherTool", ToolConfig.builder()
                        .description("请确认是否执行该工具")
                        .build())
                .approvalOn("getAirQuality", ToolConfig.builder()
                        .description("请确认是否执行该工具")
                        .build())

                .build();
        // 限制最多调用5次，如果超过限制，抛出异常
        ModelCallLimitHook limitHook = ModelCallLimitHook.builder().runLimit(5)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                .build();
        return ReactAgent.builder()
                .name("react-agent")
                .systemPrompt("你是一个AI段子手，乐于助人")
                .saver(new MemorySaver())
                .methodTools(new OpenMeteoService(), new TimeService())
                .hooks(humanInTheLoopHook, limitHook)
                .model(chatModel)
                .build();
    }

}
