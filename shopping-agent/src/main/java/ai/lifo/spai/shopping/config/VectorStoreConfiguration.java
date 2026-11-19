package ai.lifo.spai.shopping.config;

import org.springframework.context.annotation.Configuration;

/**
 * @author zhanglifeng
 * @since 2026-10-26
 */
@Configuration
public class VectorStoreConfiguration {

//    @Bean
//    ElasticsearchVectorStore elasticsearchVectorStore(ElasticsearchVectorStoreProperties properties,
//                                                      RestClient restClient,
//                                                      EmbeddingModel embeddingModel,
//                                                      ObjectProvider<ObservationRegistry> observationRegistry,
//                                                      ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
//                                                      BatchingStrategy batchingStrategy) {
//        ElasticsearchVectorStoreOptions elasticsearchVectorStoreOptions = new ElasticsearchVectorStoreOptions();
//
//        PropertyMapper mapper = PropertyMapper.get();
//        mapper.from(properties::getIndexName).whenHasText().to(elasticsearchVectorStoreOptions::setIndexName);
//        mapper.from(properties::getDimensions).whenNonNull().to(elasticsearchVectorStoreOptions::setDimensions);
//        mapper.from(properties::getSimilarity).whenNonNull().to(elasticsearchVectorStoreOptions::setSimilarity);
//        mapper.from(properties::getEmbeddingFieldName)
//                .whenHasText()
//                .to(elasticsearchVectorStoreOptions::setEmbeddingFieldName);
//
//        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
//                .options(elasticsearchVectorStoreOptions)
//                .initializeSchema(properties.isInitializeSchema())
//                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
//                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
//                .batchingStrategy(batchingStrategy)
//                .build();
//    }
}
