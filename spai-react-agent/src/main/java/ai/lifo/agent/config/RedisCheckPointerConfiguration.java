package ai.lifo.agent.config;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.JacksonStateSerializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis CheckPointer 配置
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Configuration
@RequiredArgsConstructor
public class RedisCheckPointerConfiguration {

    private final RedissonClient redissonClient;

    @Bean
    public RedisSaver redisSaver() {

        // 序列化器定制化修改
        StateSerializer stateSerializer = StateGraph.DEFAULT_JACKSON_SERIALIZER;
        if (stateSerializer instanceof JacksonStateSerializer jacksonSerializer) {
            ObjectMapper objectMapper = jacksonSerializer.objectMapper();
            // 1. 配置序列化选项
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        return RedisSaver.builder()
                .redisson(redissonClient)
                .stateSerializer(stateSerializer)
                .build();
    }

}
