package ai.lifo.spai.example.component;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-10-06
 */
public class MyTikaDocumentReader {


    private final Resource resource;

    public MyTikaDocumentReader(Resource resource) {
        this.resource = resource;
    }

    public List<Document> loadText() {
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(this.resource);
        return tikaDocumentReader.read();
    }
}
