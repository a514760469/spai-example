package ai.lifo.spai.example.langchain4j.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * @author zhanglifeng
 * @since 2025-06-03
 */
@AiService
public interface Assistant {

    @SystemMessage("You are a polite assistant")
    String chat(String userMessage);

}
