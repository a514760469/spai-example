package ai.lifo.agent.controller.rag;

import ai.lifo.agent.service.RagService;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class RagController {

    private final RagService localRagService;

    @GetMapping("/rag/importDocument")
    public String importDocument(String filePath) {
        localRagService.importDocuments(filePath);
        return "Import completed";
    }

    @GetMapping("/rag")
    public Flux<String> generate(@RequestParam(value = "message",
            defaultValue = "how to get start with spring ai alibaba?") String message) {
        return localRagService.retrieve(message).mapNotNull(x -> x.getResult().getOutput().getText());
    }

    @GetMapping("/agent/generate")
    public Flux<String> agentGenerate(@RequestParam(value = "message",
            defaultValue = "how to get start with spring ai alibaba?") String message) throws GraphRunnerException {
        return localRagService.agentGenerate(message);
    }

}
