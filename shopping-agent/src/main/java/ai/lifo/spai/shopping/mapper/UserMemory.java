package ai.lifo.spai.shopping.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户长期记忆（偏好/画像事实）：user_key + content 唯一键做精确去重，
 * 语义相近的合并交给抽取 prompt 约束措辞。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Data
@TableName("user_memory")
public class UserMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户身份键（登录手机号） */
    private String userKey;

    /** PREFERENCE / PROFILE */
    private String memoryType;

    private String content;

    private String sourceThread;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}