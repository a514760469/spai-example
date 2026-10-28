package ai.lifo.agent.config;

import ai.lifo.agent.hook.LoggingHook;
import ai.lifo.agent.hook.LongTermMemoryHook;
import ai.lifo.agent.tool.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.stores.FileSystemStore;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
     * RAG agent
     */
    @Bean
    public ReactAgent ragAgent(ChatModel chatModel, ElasticsearchVectorStore vectorStore) {

        // 创建工具回调
        DocumentSearchTool searchTool = new DocumentSearchTool(vectorStore);
        ToolCallback searchCallback = FunctionToolCallback.builder("search_documents", searchTool::search)
                .description("搜索文档以查找相关信息")
                .inputType(DocumentSearchTool.Request.class)
                .build();

        return ReactAgent.builder()
                .name("rag-agent")
                .systemPrompt("你是一个智能助手。当需要查找信息时，使用search_documents工具。基于检索到的信息回答用户的问题，并引用相关片段。")
                .model(chatModel)
                .tools(searchCallback)
//                .hooks(new RagMessageHook(vectorStore))
                .build();
    }

    @Bean
    public WebSearchTool webSearchTool(RestClient.Builder builder) {
        return new WebSearchTool(builder);   // RestClient.Builder 由 Spring Boot 自动配置
    }

    /**
     * react agent 注入
     */
    @Bean
    public ReactAgent reactAgent(ChatModel chatModel, WebSearchTool webSearchTool) {
        // shell tool 需要一个配套的ShellToolAgentHook
//        ShellTool2 shellTool = ShellTool2.builder(".workspace").build();
        ShellToolAgentHook shellToolAgentHook = ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(".workspace").build())
                .build();

        // 联网搜索
        ToolCallback webSearch = FunctionToolCallback.builder("web_search", webSearchTool::search)
                .description("""
                搜索互联网获取实时信息。涉及新闻、时事、最新版本/价格/数据，或你不确定的事实时使用。
                返回带编号的结果，回答时用 [1][2] 标注来源；不要编造结果里没有的内容。
                """)
                .inputType(WebSearchTool.Request.class)
                .build();

        // 创建 hook
        Hook humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("getWeatherTool", "请确认是否执行该工具")
                .approvalOn("getAirQuality", "请确认是否执行该工具")
                .approvalOn("shell", "请确认shell是否执行？")
                .build();
        // 限制最多调用5次，如果超过限制，抛出异常
        ModelCallLimitHook limitHook = ModelCallLimitHook.builder()
                .runLimit(5)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                .build();

        MysqlSaver mysqlSaver = MysqlSaver.builder()
                .dataSource(dataSource)
                .build();


        return ReactAgent.builder()
//                .enableLogging(true)
                .name("react-agent")
                .systemPrompt("你是一个AI段子手，乐于助人")
                .saver(mysqlSaver)
                .methodTools(new OpenMeteoService(), new TimeService())
                .tools(UserInfoTool.getUserInfoFunction(), UserInfoSaveTool.saveUserInfoFunction(), webSearch)
                .hooks(humanInTheLoopHook, limitHook, new LoggingHook(), shellToolAgentHook, new LongTermMemoryHook())
                .model(chatModel)
                .build();
    }

}
