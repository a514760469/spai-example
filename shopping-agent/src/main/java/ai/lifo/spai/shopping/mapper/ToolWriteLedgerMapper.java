package ai.lifo.spai.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * tool_write_ledger 数据访问层。
 * 账本流转依赖条件更新（如 IN_FLIGHT→SUCCESS 带 status 前置校验、防并发覆盖），
 * 用 LambdaUpdateWrapper/LambdaQueryWrapper 在调用方组装，无自定义 SQL、无 XML。
 *
 * @author zhanglifeng
 * @since 2026-11-21
 */
@Mapper
public interface ToolWriteLedgerMapper extends BaseMapper<ToolWriteLedger> {
}
