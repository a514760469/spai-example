package ai.lifo.spai.shopping.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 写操作账本（客户端幂等）：一次有业务意义的写工具调用一行，键为 idem_key 唯一索引。
 * 生命周期：调用前插入 IN_FLIGHT → 终态 SUCCESS / UNKNOWN；业务拒绝为确定性失败，由调用方删行放行重试。
 *
 * @author zhanglifeng
 * @since 2026-11-21
 */
@Data
@TableName("tool_write_ledger")
public class ToolWriteLedger {

    public static final String STATUS_IN_FLIGHT = "IN_FLIGHT";

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_UNKNOWN = "UNKNOWN";

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * SHA-256(rootThreadId + toolName + 剔除 sessionId 后的规范化业务参数)。
     * 插入时主键/唯一索引冲突 = 已有同键执行在途或已完成，交由调用方按已有行处理。
     */
    private String idemKey;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 状态 IN_FLIGHT / SUCCESS / UNKNOWN
     */
    private String status;

    /**
     * SUCCESS 时缓存的原始工具结果，重复调用直接原样返回
     */
    private String response;

    /**
     * UNKNOWN 状态下已消耗的有界重发次数（DB DEFAULT 0，接管重发时 +1）
     */
    private Integer retryCount;

    /**
     * 以下两列由数据库默认值维护（insert/update 置 null 即被 MP 跳过，走 DDL 的 DEFAULT / ON UPDATE）
     */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
