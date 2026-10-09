package ai.lifo.spai.example.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanglifeng
 * @since 2026-10-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceInit {

    private final VectorStore qdrantVectorStore;

    @Value("classpath:data/story-1.md")
    public Resource file1;

    @Value("classpath:data/story-2.md")
    public Resource file2;

    public void run1() {
        log.info("Loading *.md files as Documents");

        var markdownReader1 = new MarkdownDocumentReader(file1, MarkdownDocumentReaderConfig.builder()
                .withAdditionalMetadata("location", "North Pole")
                .build());
        List<Document> documents = new ArrayList<>(markdownReader1.get());

        var markdownReader2 = new MarkdownDocumentReader(file2, MarkdownDocumentReaderConfig.builder()
                .withAdditionalMetadata("location", "Italy")
                .build());
        documents.addAll(markdownReader2.get());

        log.info("Creating and storing Embeddings from Documents");

        qdrantVectorStore.add(new TokenTextSplitter().split(documents));
    }

    @PostConstruct
    public void run() {
        List<Document> documents = new ArrayList<>();
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("category", "技术文档");
        metadata1.put("word", "什么是hybridSearch？它是一种混合检索技术");
        Document document1 = Document.builder()
                .id("1")
                .text("什么是hybridSearch")
                .metadata(metadata1)
                .build();
        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("category", "HybridSearch");
        metadata2.put("word", "HybridSearch结合了向量搜索和传统文本检索的优势");
        Document document2 = Document.builder()
                .id("2")
                .text("HybridSearch的优势")
                .metadata(metadata2)
                .build();
        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("category", "其他");
        metadata3.put("word", "无关文档内容");
        Document document3 = Document.builder()
                .id("3")
                .text("这是一个无关的文档")
                .metadata(metadata3)
                .build();
        documents.add(document1);
        documents.add(document2);
        documents.add(document3);
        qdrantVectorStore.add(documents);
        log.info("add documents success");
    }
}
