package ai.lifo.spai.rag.controller;

import ai.lifo.spai.rag.entity.ReadDocumentRequest;
import ai.lifo.spai.rag.service.EnterpriseRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author zhanglifeng
 * @since 2026-10-09
 */
@Slf4j
@RequestMapping("/rag/reader")
@RestController
@RequiredArgsConstructor
public class RagReaderController {

    private final EnterpriseRagService enterpriseRagService;

    @PostMapping("/read")
    public String readDocument(@RequestBody ReadDocumentRequest request) {

        enterpriseRagService.readResource(request);
        return "readDocument ok";
    }


    @GetMapping("/read")
    public String read(String url) {

        enterpriseRagService.readResource(new ReadDocumentRequest(url, null));
        return "readDocument ok";
    }
}
