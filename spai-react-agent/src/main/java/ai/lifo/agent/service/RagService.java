package ai.lifo.agent.service;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * @author zhanglifeng
 * @since 2026-10-28
 */
public interface RagService {

    void importDocuments(String filePath);

    Flux<ChatResponse> retrieve(String message);

    Flux<String> agentGenerate(String message) throws GraphRunnerException;
}
