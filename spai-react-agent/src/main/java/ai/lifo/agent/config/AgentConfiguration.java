package ai.lifo.agent.config;

import ai.lifo.agent.hook.LoggingHook;
import ai.lifo.agent.tool.OpenMeteoService;
import ai.lifo.agent.tool.TimeService;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.stores.DatabaseStore;
import com.alibaba.cloud.ai.graph.store.stores.FileSystemStore;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * @author zhanglifeng
 * @since 2026-10-10
 */
@Configuration
@RequiredArgsConstructor
public class AgentConfiguration {

    private final DataSource dataSource;

    @Component
    static class ExcludeBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor {

        // 使用自己的executionController，需要排除默认的 executionController
        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            registry.removeBeanDefinition("executionController");
        }
    }


    /**
     * 长期记忆存储
     */
    @Bean
    public Store databaseStore() {
        return new FileSystemStore(".workspace/store");
//        return new DatabaseStore(dataSource);
    }


    /**
     * react agent 注入
     */
    @Bean
    public ReactAgent reactAgent(ChatModel chatModel) {
        // shell tool 需要一个配套的ShellToolAgentHook
//        ShellTool2 shellTool = ShellTool2.builder(".workspace").build();
        ShellToolAgentHook shellToolAgentHook = ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(".workspace").build())
                .build();
//        ToolCallback shellTool = ShellTool.builder(".workspace")
//                .withName("shell")
//                .build();

        // 创建 hook
        Hook humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("getWeatherTool", "请确认是否执行该工具")
                .approvalOn("getAirQuality", "请确认是否执行该工具")
                .approvalOn("shell", "请确认是否执行？")
                .build();
        // 限制最多调用5次，如果超过限制，抛出异常
        ModelCallLimitHook limitHook = ModelCallLimitHook.builder().runLimit(5)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                .build();

        MysqlSaver mysqlSaver = MysqlSaver.builder()
                .dataSource(dataSource)
                .build();


        return ReactAgent.builder()
                .name("react-agent")
                .systemPrompt("你是一个AI段子手，乐于助人")
                .saver(mysqlSaver)
                .methodTools(new OpenMeteoService(), new TimeService())
//                .tools(UserInfoTool.getUserInfoFunction())
                .hooks(humanInTheLoopHook, limitHook, new LoggingHook(), shellToolAgentHook)
                .model(chatModel)
                .build();
    }

}
