package ai.lifo.spai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author zhanglifeng
 * @since 2026-09-28 
 */
@Mapper
public interface CustomChatMemoryMapper extends BaseMapper<CustomChatMemory> {

    @Select("SELECT DISTINCT conversation_id FROM custom_chat_memory where deleted = 0")
    List<String> findConversationIds();
}