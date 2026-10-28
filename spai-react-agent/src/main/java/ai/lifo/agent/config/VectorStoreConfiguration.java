package ai.lifo.agent.config;

import io.micrometer.observation.ObservationRegistry;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-10-26
 */
@Configuration
public class VectorStoreConfiguration {

    @Bean
    ElasticsearchVectorStore elasticsearchVectorStore(ElasticsearchVectorStoreProperties properties,
                                                      RestClient restClient,
                                                      EmbeddingModel embeddingModel,
                                                      ObjectProvider<ObservationRegistry> observationRegistry,
                                                      ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
                                                      BatchingStrategy batchingStrategy) {
        ElasticsearchVectorStoreOptions elasticsearchVectorStoreOptions = new ElasticsearchVectorStoreOptions();

        PropertyMapper mapper = PropertyMapper.get();
        mapper.from(properties::getIndexName).whenHasText().to(elasticsearchVectorStoreOptions::setIndexName);
        mapper.from(properties::getDimensions).whenNonNull().to(elasticsearchVectorStoreOptions::setDimensions);
        mapper.from(properties::getSimilarity).whenNonNull().to(elasticsearchVectorStoreOptions::setSimilarity);
        mapper.from(properties::getEmbeddingFieldName)
                .whenHasText()
                .to(elasticsearchVectorStoreOptions::setEmbeddingFieldName);

        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(elasticsearchVectorStoreOptions)
                .initializeSchema(properties.isInitializeSchema())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
                .batchingStrategy(batchingStrategy)
                .build();
    }
}
