package ai.lifo.agent.service;

import ai.lifo.agent.controller.OutputUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalRagService implements RagService {


    private static final String textField = "content";

    private static final String vectorField = "embedding";

    @Value("classpath:/prompts/system-qa.txt")
    private Resource systemResource;

    private final ChatModel chatModel;

    private final VectorStore elasticsearchVectorStore;

    private final RerankModel rerankModel;

    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchVectorStoreProperties options;

    private final ReactAgent ragAgent;

    @Override
    public void importDocuments(String filePath) {

        // 1. parse document
        Resource resource = new FileSystemResource(filePath);
        DocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        log.info("{} documents 已加载", documents.size());

        // 2. split trunks
        List<Document> splitDocuments = new TokenTextSplitter().apply(documents);
        log.info("{} documents split", splitDocuments.size());

        // 3. create embedding and store to vector store
        log.info("create embedding and save to vector store");
        createIndexIfNotExists();
        elasticsearchVectorStore.add(splitDocuments);
    }

    private void createIndexIfNotExists() {
        try {
            String indexName = options.getIndexName();
            Integer dimsLength = options.getDimensions();

            if (StringUtils.isBlank(indexName)) {
                throw new IllegalArgumentException("Elastic search index name must be provided");
            }

            boolean exists = elasticsearchClient.indices().exists(idx -> idx.index(indexName)).value();
            if (exists) {
                log.debug("Index {} already exists. Skipping creation.", indexName);
                return;
            }

            String similarityAlgo = options.getSimilarity().name();
            IndexSettings indexSettings = IndexSettings.of(settings -> settings.numberOfShards(String.valueOf(1)).numberOfReplicas(String.valueOf(1)));

            // Maybe using json directly?
            Map<String, Property> properties = new HashMap<>();
            properties.put(vectorField, Property.of(property -> property.denseVector(
                    DenseVectorProperty.of(dense -> dense.index(true).dims(dimsLength).similarity(DenseVectorSimilarity.valueOf(similarityAlgo))))));
            properties.put(textField, Property.of(property -> property.text(TextProperty.of(t -> t))));

            Map<String, Property> metadata = new HashMap<>();
            metadata.put("ref_doc_id", Property.of(property -> property.keyword(KeywordProperty.of(k -> k))));

            properties.put("metadata",
                    Property.of(property -> property.object(ObjectProperty.of(op -> op.properties(metadata)))));

            CreateIndexResponse indexResponse = elasticsearchClient.indices()
                    .create(createIndexBuilder -> createIndexBuilder.index(indexName)
                            .settings(indexSettings)
                            .mappings(TypeMapping.of(mappings -> mappings.properties(properties))));

            if (!indexResponse.acknowledged()) {
                throw new RuntimeException("failed to create index");
            }

            log.info("create elasticsearch index {} successfully", indexName);
        }
        catch (IOException e) {
            log.error("failed to create index", e);
            throw new RuntimeException(e);
        }
    }


    @Override
    public Flux<ChatResponse> retrieve(String message) {
        // Enable hybrid search, both embedding and full text search
        SearchRequest searchRequest = SearchRequest.builder().
                topK(4)
                .similarityThresholdAll()
                .filterExpression(new FilterExpressionBuilder().eq(textField, message).build())
                .build();

        // Step3 - Retrieve and llm generate
        SystemPromptTemplate sysPrompt = new SystemPromptTemplate(systemResource);
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new RetrievalRerankAdvisor(elasticsearchVectorStore, rerankModel, searchRequest, sysPrompt, 0.1))
                .build();

        return chatClient.prompt().user(message).stream().chatResponse();
    }

    @Override
    public Flux<String> agentGenerate(String message) throws GraphRunnerException {
        // 调用 Agent（RAG 检索只会在 Agent 开始时执行一次）

        Flux<NodeOutput> stream = ragAgent.stream(message);
        return stream.doOnNext(nodeOutput -> {
            System.out.println("节点输出: " + JSON.toJSONString(nodeOutput, SerializerFeature.PrettyFormat));
        }).map(nodeOutput -> {
            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                return OutputUtil.obtainThinkingOutputStreaming(streamingOutput);
            }
            return "";
        });
    }


}
