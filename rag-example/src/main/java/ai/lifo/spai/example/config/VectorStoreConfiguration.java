package ai.lifo.spai.example.config;

import io.micrometer.observation.ObservationRegistry;
import io.qdrant.client.QdrantClient;
import org.neo4j.driver.Driver;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreProperties;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

/**
 * @author zhanglifeng
 * @since 2026-10-03
 */
@Configuration
public class VectorStoreConfiguration {

    /**
     * neo4j 向量库，
     *
     * @see org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration
     */
    @Bean
    public Neo4jVectorStore neo4jVectorStore(Driver driver, EmbeddingModel embeddingModel,
                                        Neo4jVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
                                        ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
                                        BatchingStrategy batchingStrategy) {

        return Neo4jVectorStore.builder(driver, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
                .batchingStrategy(batchingStrategy)
                .databaseName(properties.getDatabaseName())
                .embeddingDimension(properties.getEmbeddingDimension() != null ? properties.getEmbeddingDimension()
                        : embeddingModel.dimensions())
                .distanceType(properties.getDistanceType())
                .label(properties.getLabel())
                .embeddingProperty(properties.getEmbeddingProperty())
                .indexName(properties.getIndexName())
                .idProperty(properties.getIdProperty())
                .constraintName(properties.getConstraintName())
                .textProperty(properties.getTextProperty())
                .build();
    }


    /**
     * redis 向量库，
     *
     * @see org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration
     */
    @Bean
    public RedisVectorStore redisVectorStore(EmbeddingModel embeddingModel,
                                             RedisVectorStoreProperties properties,
                                             JedisConnectionFactory jedisConnectionFactory,
                                             ObjectProvider<ObservationRegistry> observationRegistry,
                                             ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
                                             BatchingStrategy batchingStrategy) {

        JedisPooled jedisPooled = this.jedisPooled(jedisConnectionFactory);
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
                .batchingStrategy(batchingStrategy)
                .indexName(properties.getIndexName())
                .prefix(properties.getPrefix())
                .metadataFields(RedisVectorStore.MetadataField.tag("subject"),
                        RedisVectorStore.MetadataField.numeric("year"))
                .build();
    }

    private JedisPooled jedisPooled(JedisConnectionFactory jedisConnectionFactory) {

        String host = jedisConnectionFactory.getHostName();
        int port = jedisConnectionFactory.getPort();

        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .ssl(jedisConnectionFactory.isUseSsl())
                .clientName(jedisConnectionFactory.getClientName())
                .timeoutMillis(jedisConnectionFactory.getTimeout())
                .password(jedisConnectionFactory.getPassword())
                .build();

        return new JedisPooled(new HostAndPort(host, port), clientConfig);
    }

    /**
     * pg 向量库，
     *
     * @see org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration
     */
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate,
                                       EmbeddingModel embeddingModel,
                                       PgVectorStoreProperties properties,
                                       ObjectProvider<ObservationRegistry> observationRegistry,
                                       ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
                                       BatchingStrategy batchingStrategy) {

        var initializeSchema = properties.isInitializeSchema();

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName(properties.getSchemaName())
                .idType(properties.getIdType())
                .vectorTableName(properties.getTableName())
                .vectorTableValidationsEnabled(properties.isSchemaValidation())
                .dimensions(properties.getDimensions())
                .distanceType(properties.getDistanceType())
                .removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
                .indexType(properties.getIndexType())
                .initializeSchema(initializeSchema)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
                .batchingStrategy(batchingStrategy)
                .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
                .build();
    }


    /**
     * qdrant vector store
     */
    @Bean
    public QdrantVectorStore qdrantVectorStore(EmbeddingModel embeddingModel, QdrantVectorStoreProperties properties,
                                         QdrantClient qdrantClient, ObjectProvider<ObservationRegistry> observationRegistry,
                                         ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
                                         BatchingStrategy batchingStrategy) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(properties.getCollectionName())
                .initializeSchema(properties.isInitializeSchema())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
                .batchingStrategy(batchingStrategy)
                .build();
    }
}
