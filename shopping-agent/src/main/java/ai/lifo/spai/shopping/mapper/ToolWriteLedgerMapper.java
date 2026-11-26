package ai.lifo.spai.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * tool_write_ledger 数据访问层。状态流转用带 status 前置条件的更新防并发覆盖，Wrapper 在调用方组装。
 *
 * @author zhanglifeng
 * @since 2026-11-21
 */
@Mapper
public interface ToolWriteLedgerMapper extends BaseMapper<ToolWriteLedger> {
}
