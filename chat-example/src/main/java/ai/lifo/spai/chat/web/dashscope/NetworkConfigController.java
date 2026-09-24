package ai.lifo.spai.chat.web.dashscope;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * 演示自定义 httpClient 以解决请求模型过程中的网络问题
 * 对于 Stream，将 WebClient 的底层引擎替换为 NettyHttpClient，并优化 Netty 资源池和连接复用
 * 对于 Call，将 RestClient 替换为 OkHttpClient/JDK HttpClient。合理设置 ConnectionPool 和 ReadTimeOut
 *
 * @author zhanglifeng
 * @since 2026-09-18
 */
@Slf4j
@RequestMapping("/network")
@RestController
public class NetworkConfigController {

    private final ChatClient dashScopeChatClient;

    public NetworkConfigController() {

        this.dashScopeChatClient = ChatClient.builder(getDashScopeChatModel())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    private DashScopeChatModel getDashScopeChatModel() {

        return DashScopeChatModel.builder()
                .dashScopeApi(getDashscopeAPI()).defaultOptions(
                        DashScopeChatOptions.builder()
                                .model("qwen3.7-flash-2026-07-15")
                                .enableSearch(true)
                                .searchOptions(DashScopeApiSpec.SearchOptions.builder()
                                        .enableSource(true)
                                        .forcedSearch(true)
                                        .searchStrategy("turbo")
                                        .build()
                                ).build()
                ).build();
    }

    private static DashScopeApi getDashscopeAPI() {

        // 配置HTTP连接池
        ConnectionProvider provider = ConnectionProvider.builder("dashscope")
                .maxConnections(500)
                .maxIdleTime(Duration.ofMinutes(10))        // 空闲连接保持10分钟
                .maxLifeTime(Duration.ofMinutes(30))        // 连接最大生命周期30分钟
                .evictInBackground(Duration.ofSeconds(60))  // 每60秒清理一次过期连接
                .build();

        // 配置HTTP客户端
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)  // 连接超时10秒
                .responseTimeout(Duration.ofSeconds(300))              // 响应总超时，60秒对于流式调用很容易超时设置300
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60))   // 读超时60秒
                        .addHandlerLast(new WriteTimeoutHandler(10))  // 写超时10秒
                );

        // 构建WebClient实例，可以精确配置参数，虽然默认也会使用netty的Client，但是需要反射能够找到
        WebClient.Builder webClientbuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 可选配置 添加请求日志记录功能
                .filter(ExchangeFilterFunction.ofRequestProcessor(
                        clientRequest -> {
                            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
                            return Mono.just(clientRequest);
                        }
                ))
                // 添加响应日志记录功能
                .filter(ExchangeFilterFunction.ofResponseProcessor(
                        clientResponse -> {
                            log.info("Response status: {}", clientResponse.statusCode());
                            return Mono.just(clientResponse);
                        }
                ));

        // 默认会使用SimpleClientHttpRequestFactory 替换成JdkClientHttpRequestFactory
        java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(60));

        return DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .webClientBuilder(webClientbuilder)
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();
    }


    @GetMapping("/call")
    public String testCall() {
        return dashScopeChatClient.prompt("hi").call().content();
    }

    @GetMapping(value = "/stream", produces = "text/plain;charset=UTF-8")
    public Flux<String> testStream() {
        return dashScopeChatClient.prompt("hi").stream().content();
    }

}
