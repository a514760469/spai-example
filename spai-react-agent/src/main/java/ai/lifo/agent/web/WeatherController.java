package ai.lifo.agent.web;

import ai.lifo.agent.prompt.WeatherPrompt;
import ai.lifo.agent.response.ResponseFormat;
import ai.lifo.agent.tool.UserLocationTool;
import ai.lifo.agent.tool.WeatherForLocationTool;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanglifeng
 * @since 2026-01-15
 */
@RestController
public class WeatherController implements InitializingBean {

    private ReactAgent agent;

    @Override
    public void afterPropertiesSet() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();

        // Note: model must be set when use options build.
        // temperature 0.0 - 1.0 越高越有创造性
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .temperature(0.5)
                        .maxToken(1000)
                        .build())
                .build();

        ToolCallback weatherTool = FunctionToolCallback.builder("GetWeatherForLocation", new WeatherForLocationTool())
                .description("Get weather for a given city")
                .inputType(String.class)
                .build();

        ToolCallback getUserLocationTool = FunctionToolCallback.builder("GetUserLocation", new UserLocationTool())
                .description("Retrieve user location based on user ID")
                .inputType(String.class)
                .build();


        // 限制最多调用5次，如果超过限制，抛出异常
        ModelCallLimitHook hook = ModelCallLimitHook.builder().runLimit(5)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                .build();

        // 创建 Agent
        agent = ReactAgent.builder()
                .name("weather_agent")
                .model(chatModel)
                .hooks(hook)
                .tools(weatherTool, getUserLocationTool)
                .systemPrompt(WeatherPrompt.SYSTEM_PROMPT)
                .outputType(ResponseFormat.class)
                .saver(new MemorySaver())
                .build();
    }


    @GetMapping("/weather")
    public String chat(String id) throws GraphRunnerException {

        // threadId 是给定对话的唯一标识符
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(id)
                .addMetadata("user_id", "1").build();

        // 第一次调用
        AssistantMessage response = agent.call("what is the weather outside?", runnableConfig);
        System.out.println(response.getText());
        // 输出类似：
        // Florida is still having a 'sun-derful' day! The sunshine is playing
        // 'ray-dio' hits all day long! I'd say it's the perfect weather for
        // some 'solar-bration'!

        // 注意我们可以使用相同的 threadId 继续对话
        response = agent.call("thank you!", runnableConfig);
        System.out.println(response.getText());
        // 输出类似：
        // You're 'thund-erfully' welcome! It's always a 'breeze' to help you
        // stay 'current' with the weather.

        return response.getText();
    }
}
