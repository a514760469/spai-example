package ai.lifo.spai.rag.service.impl;

import ai.lifo.spai.rag.entity.ReadDocumentRequest;
import ai.lifo.spai.rag.service.EnterpriseRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-10-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseRagServiceImpl implements EnterpriseRagService {

    private final VectorStore vectorStore;


    @Override
    public void readResource(ReadDocumentRequest request) {
        ExtractedTextFormatter extractedTextFormatter = ExtractedTextFormatter.builder()
//                .withLeftAlignment(true)
                .build();

        var tikaDocumentReader = new TikaDocumentReader(request.resourceUrl(), extractedTextFormatter);
        List<Document> docs = tikaDocumentReader.read();
        List<Document> addDocs = docs.stream().filter(doc -> {
            String text = doc.getText();
            log.info("文档：{}", text);
            if (text == null || text.isBlank()) {
                log.warn("过滤掉空白文档, metadata: {}", doc.getMetadata());
                return false;
            }
            return true;
        }).peek(doc -> {
            log.info("加载文档: {}", doc.getText());
            doc.getMetadata().put("loadedAt", LocalDateTime.now());
            doc.getMetadata().put("fileId", request.resourceUrl());
        }).toList();

        log.info("加载文档: {} docs size: {}", request.resourceUrl(), addDocs.size());
        if (addDocs.isEmpty()) {
            log.warn("没有有效文档: {}", request.resourceUrl());
        }
        else {
            vectorStore.add(addDocs);
        }
    }
}
