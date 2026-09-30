package ai.lifo.spai.chat.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author zhanglifeng
 * @since 2026-09-28 
 */
@Data
public class CustomChatMemory {
    /**
    * 主键
    */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
    * conversation 
    */
    private String conversationId;

    /**
    * 消息内容
    */
    private String message;

    /**
    * 模型名称
    */
    private String model;

    /**
    * message 类型
    */
    private String type;

    /**
    * 创建时间
    */
    private LocalDateTime createdAt;

    @TableLogic
    private Boolean deleted;
}