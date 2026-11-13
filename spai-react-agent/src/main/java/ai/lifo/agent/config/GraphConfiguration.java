package ai.lifo.agent.config;

import ai.lifo.agent.graph.EmailCompiledGraph;
import ai.lifo.agent.graph.TranslateCompiledGraph;
import ai.lifo.agent.graph.chat.ChatCompiledGraph;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author zhanglifeng
 * @since 2026-11-04
 */
@Configuration
@RequiredArgsConstructor
public class GraphConfiguration {

    private final DataSource dataSource;

    private final RedisSaver redisSaver;

    /**
     * 测试邮件处理graph
     */
    @Bean
    public CompiledGraph emailGraph(ChatModel chatModel) throws GraphStateException {
        MysqlSaver mysqlSaver = new MysqlSaver.Builder().dataSource(dataSource).build();
        return EmailCompiledGraph.createEmailAgentGraph(chatModel, mysqlSaver);
    }

    /**
     * 聊天graph
     */
    @Bean
    public CompiledGraph chatGraph(ChatModel chatModel) throws GraphStateException {
        return ChatCompiledGraph.createGraph(chatModel, redisSaver);
    }

    @Bean
    public CompiledGraph translateCompiledGraph(ChatModel chatModel) throws GraphStateException {
        return TranslateCompiledGraph.createGraph(chatModel, redisSaver);
    }

}
