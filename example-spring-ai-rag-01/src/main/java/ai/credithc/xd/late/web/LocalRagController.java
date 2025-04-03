package ai.credithc.xd.late.web;

import ai.credithc.xd.late.service.LocalRagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author zhanglifeng
 * @since 2025-04-03
 */
@RestController
@RequestMapping("/ai")
public class LocalRagController {

    private final LocalRagService localRagService;

    public LocalRagController(LocalRagService localRagService) {
        this.localRagService = localRagService;
    }

    @GetMapping("/rag/importDocument")
    public void importDocuments() {
        localRagService.importDocuments();
    }

    @GetMapping("/rag")
    public Flux<String> generate(@RequestParam(value = "message", defaultValue = "how to get start with spring ai alibaba?")
                                     String message) {
        return localRagService.retrieve(message).map(x -> x.getResult().getOutput().getText());
    }
}
