package ai.lifo.spai.shopping.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品语义索引与记忆索引的 Redis VectorStore。
 * metadataFields 必须显式声明：未声明的字段不进 FT 索引 schema，既不能参与 filter，也不会出现在检索结果 metadata 中。
 * 元数据 key 用裸字段名（不带 metadata. 前缀）；initializeSchema=true 仅在索引不存在时创建，
 * 改字段声明需先 FT.DROPINDEX 再重启。
 * 本 Bean 覆盖 starter 自动配置（其 vectorStore 为 ConditionalOnMissingBean），因为自动配置不声明 metadataFields。
 *
 * @author zhanglifeng
 * @since 2026-10-26
 */
@Configuration
public class VectorStoreConfiguration {

    public static final String MEMORY_INDEX_NAME = "user-memory-idx";

    public static final String MEMORY_KEY_PREFIX = "memory:";

    /** DashScope embeddings 单次硬限制 25 条文本；默认 TokenCountBatchingStrategy 只按 token 分批、不限条数，需自行按条数分批。 */
    private static final int EMBEDDING_BATCH_LIMIT = 25;

    @Bean
    public JedisPooled jedisPooled(RedisProperties redisProperties) {
        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder();
        if (redisProperties.getPassword() != null) {
            clientConfig.password(redisProperties.getPassword());
        }
        return new JedisPooled(new HostAndPort(redisProperties.getHost(), redisProperties.getPort()), clientConfig.database(redisProperties.getDatabase()).build());
    }

    /** 商品库是主语义库；记忆库用 @Qualifier 显式注入 */
    @Bean
    @Primary
    public RedisVectorStore productVectorStore(EmbeddingModel embeddingModel,
                                               JedisPooled jedisPooled,
                                               RedisVectorStoreProperties properties,
                                               ObjectProvider<ObservationRegistry> observationRegistry,
                                               ObjectProvider<VectorStoreObservationConvention> customObservationConvention) {

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(properties.getIndexName())
                .prefix(properties.getPrefix())
                .initializeSchema(properties.isInitializeSchema())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfUnique(() -> null))
                .batchingStrategy(documents -> {
                    List<List<Document>> batches = new ArrayList<>();
                    for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_LIMIT) {
                        batches.add(documents.subList(i, Math.min(i + EMBEDDING_BATCH_LIMIT, documents.size())));
                    }
                    return batches;
                })
                .metadataFields(
                        MetadataField.tag("productId"),
                        MetadataField.text("title"),
                        MetadataField.tag("category"),
                        MetadataField.tag("subCategory"),
                        MetadataField.tag("brand"),
                        MetadataField.numeric("price"),
                        MetadataField.text("imageUrl"))
                .build();
    }

    /**
     * 用户长期记忆的向量索引：Document.id = user_memory 表主键，userKey 做 TAG 硬隔离；
     * 与商品索引同实例不同 index/prefix，互不污染。
     */
    @Bean
    public RedisVectorStore memoryVectorStore(EmbeddingModel embeddingModel,
                                              JedisPooled jedisPooled,
                                              ObjectProvider<ObservationRegistry> observationRegistry,
                                              ObjectProvider<VectorStoreObservationConvention> customObservationConvention) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(MEMORY_INDEX_NAME)
                .prefix(MEMORY_KEY_PREFIX)
                .initializeSchema(true)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfUnique(() -> null))
                .metadataFields(
                        MetadataField.tag("userKey"),
                        MetadataField.tag("memoryType"))
                .build();
    }
}
