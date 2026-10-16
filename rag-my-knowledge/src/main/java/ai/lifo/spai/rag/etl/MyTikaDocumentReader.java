package ai.lifo.spai.rag.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-10-09
 */
public class MyTikaDocumentReader {


    private final Resource resource;

    public MyTikaDocumentReader(Resource resource) {
        this.resource = resource;
    }

    public List<Document> loadText() {
        var tikaDocumentReader = new TikaDocumentReader(this.resource);
        return tikaDocumentReader.read();
    }
}
