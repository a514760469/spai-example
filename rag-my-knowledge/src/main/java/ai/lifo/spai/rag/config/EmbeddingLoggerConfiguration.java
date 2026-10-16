package ai.lifo.spai.rag.config;

import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 装饰器模式包装EmbeddingModel 实现打印日志
 *
 * @author zhanglifeng
 * @since 2026-10-09
 */
@Slf4j
@Configuration
public class EmbeddingLoggerConfiguration {

    @Bean
    @Primary
    public EmbeddingModel loggingEmbeddingModel(DashScopeEmbeddingModel delegate) {
        return new LoggingEmbeddingModel(delegate);
    }


    static class LoggingEmbeddingModel implements EmbeddingModel {

        private final DashScopeEmbeddingModel delegate;

        LoggingEmbeddingModel(DashScopeEmbeddingModel delegate) {
            this.delegate = delegate;
        }

        @NotNull
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> texts = request.getInstructions();
            log.info("Embedding Request: documents={}", texts.size());
            log.debug("Embedding Request Content: {}", texts);

            long start = System.currentTimeMillis();
            EmbeddingResponse response = delegate.call(request);
            long duration = System.currentTimeMillis() - start;

            List<Embedding> embeddings = response.getResults();
            int dimensions = embeddings.isEmpty() ? 0 : embeddings.getFirst().getOutput().length;
            log.info("Embedding Response: {} embeddings, dimensions={}, duration={}ms",
                    embeddings.size(), dimensions, duration);

            return response;
        }

        @NotNull
        @Override
        public float[] embed(@NotNull String text) {
            log.debug("Embed single text: {}", text);
            return delegate.embed(text);
        }

        @NotNull
        @Override
        public float[] embed(Document document) {
            log.debug("Embed document: {}", document.getText());
            return delegate.embed(document);
        }

        @NotNull
        @Override
        public List<float[]> embed(List<String> texts) {
            log.debug("Embed {} texts", texts.size());
            return delegate.embed(texts);
        }

        @Override
        public int dimensions() {
            int dims = delegate.dimensions();
            log.info("EmbeddingModel dimensions: {}", dims);
            return dims;
        }
    }
}
