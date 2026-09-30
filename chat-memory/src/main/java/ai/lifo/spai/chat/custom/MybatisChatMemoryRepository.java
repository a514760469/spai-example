package ai.lifo.spai.chat.custom;

import ai.lifo.spai.chat.mapper.CustomChatMemory;
import ai.lifo.spai.chat.mapper.CustomChatMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义ChatMemoryRepository 使用mybatis自己访问数据库
 * @author zhanglifeng
 * @since 2026-09-28
 */
@RequiredArgsConstructor
public class MybatisChatMemoryRepository implements ChatMemoryRepository {

    private final CustomChatMemoryMapper customChatMemoryMapper;

    @Override
    public List<String> findConversationIds() {
        return customChatMemoryMapper.findConversationIds();
    }

    @Override
    public List<Message> findByConversationId(@NonNull String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");

        List<CustomChatMemory> list = new LambdaQueryChainWrapper<>(customChatMemoryMapper)
                .eq(CustomChatMemory::getConversationId, conversationId).list();

        return list.stream().map(customChatMemory -> {
            var content = customChatMemory.getMessage();
            var type = MessageType.valueOf(customChatMemory.getType());

            AbstractMessage message;
            switch (type) {
                case USER -> message = new UserMessage(content);
                case ASSISTANT -> message = new AssistantMessage(content);
                case SYSTEM -> message = new SystemMessage(content);
                // The content is always stored empty for ToolResponseMessages.
                // If we want to capture the actual content, we need to extend
                // AddBatchPreparedStatement to support it.
                case TOOL -> message = ToolResponseMessage.builder().responses(List.of()).build();
                default -> throw new IllegalArgumentException("Unknown message type: " + type);
            }
            message.getMetadata().put("customId", customChatMemory.getId());

            return message;
        }).collect(Collectors.toList());
    }

    @Override
    public void saveAll(@NonNull String conversationId, @NonNull List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
//        this.deleteByConversationId(conversationId);

        List<CustomChatMemory> customChatMemories = new ArrayList<>();
        for (Message message : messages) {
            CustomChatMemory customChatMemory = new CustomChatMemory();
            customChatMemory.setConversationId(conversationId);
            customChatMemory.setType(message.getMessageType().name());
            customChatMemory.setMessage(message.getText());
            Object id = message.getMetadata().get("customId");
            if (id != null) {
                customChatMemory.setId(Long.parseLong(id.toString()));
            }
            customChatMemories.add(customChatMemory);
        }
        customChatMemoryMapper.insertOrUpdate(customChatMemories);
    }

    @Override
    public void deleteByConversationId(@NonNull String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");

        LambdaUpdateWrapper<CustomChatMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomChatMemory::getConversationId, conversationId);
        customChatMemoryMapper.delete(wrapper);
    }


    public static MybatisChatMemoryBuilder mysqlBuilder() {
        return new MybatisChatMemoryBuilder();
    }

    public static class MybatisChatMemoryBuilder {

        private CustomChatMemoryMapper customChatMemoryMapper;

        public MybatisChatMemoryBuilder service(CustomChatMemoryMapper customChatMemoryMapper) {
            this.customChatMemoryMapper = customChatMemoryMapper;
            return this;
        }

        public MybatisChatMemoryRepository build() {
            return new MybatisChatMemoryRepository(this.customChatMemoryMapper);
        }

    }
}
