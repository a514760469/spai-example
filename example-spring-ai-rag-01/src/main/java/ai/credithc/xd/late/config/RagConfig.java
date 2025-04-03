package ai.credithc.xd.late.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2025-04-02
 */
@Configuration
public class RagConfig {

    @Bean
    ChatClient botChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("你将作为一名机器人产品的专家，对于用户的使用需求作出解答")
                .build();
    }

    /**
     * 和ElasticsearchVectorStore冲突
     */
//    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();

        // 生成一个机器人产品说明书的文档
        List<Document> documents = List.of(
                new Document("""
                        产品说明书:产品名称：智能机器人
                        产品描述：智能机器人是一个智能设备，能够自动完成各种任务。
                        功能：
                        1. 自动导航：机器人能够自动导航到指定位置。
                        2. 自动抓取：机器人能够自动抓取物品。
                        3. 自动放置：机器人能够自动放置物品。
                        """));

        simpleVectorStore.add(documents);
        return simpleVectorStore;
    }

    @Bean
    ChatClient chatClientMultiQuery(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一位专业的室内设计顾问，精通各种装修风格、材料选择和空间布局。请基于提供的参考资料，为用户提供专业、详细且实用的建议。在回答时，请注意：\n" +
                        "1. 准确理解用户的具体需求\n" +
                        "2. 结合参考资料中的实际案例\n" +
                        "3. 提供专业的设计理念和原理解释\n" +
                        "4. 考虑实用性、美观性和成本效益\n" +
                        "5. 如有需要，可以提供替代方案")
                .build();
    }
}
