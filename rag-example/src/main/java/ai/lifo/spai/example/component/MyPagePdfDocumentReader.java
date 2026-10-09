package ai.lifo.spai.example.component;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-10-06
 */
public class MyPagePdfDocumentReader {

    private final Resource resource;

    public MyPagePdfDocumentReader(Resource resource) {
        this.resource = resource;
    }


    public List<Document> getDocsFromPdf() {

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(0)
                        .build())
                .withPagesPerDocument(1)
                .build();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);

        return pdfReader.read();
    }
}
