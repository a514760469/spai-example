package ai.lifo.spai.shopping.store;

import ai.lifo.spai.shopping.mapper.ToolWriteLedger;
import ai.lifo.spai.shopping.mapper.ToolWriteLedgerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 写操作账本：客户端侧保证同一业务键至多执行一次，不依赖 server 配合。
 * 状态流转均为带前置条件的单语句更新（WHERE idem_key=? AND status='IN_FLIGHT'），
 * 受影响行数为 0 即表示被并发抢跑，无需事务包装。
 * 状态机：IN_FLIGHT → SUCCESS（缓存结果，重复调用回放）/ UNKNOWN（有界重发，用尽后拦截待对账）；
 * 业务拒绝或确证未执行时 release 删行放行同键重试。
 * 进程崩溃遗留的 IN_FLIGHT 行超过 {@link #STALE_IN_FLIGHT} 后允许被同键调用接管。
 *
 * @author zhanglifeng
 * @since 2026-11-21
 */
@Component
@RequiredArgsConstructor
public class LedgerStore {

    /** IN_FLIGHT 独占时限：崩溃后行会停留在 IN_FLIGHT，超时后允许同键接管，否则该业务键永久无法再写。 */
    private static final Duration STALE_IN_FLIGHT = Duration.ofMinutes(10);

    /** UNKNOWN 有界重发额度：超时无法区分"未执行"与"已执行但响应丢失"，放行一次重发，再次超时即拦截待对账。 */
    private static final int MAX_AMBIGUOUS_RETRY = 1;

    private final ToolWriteLedgerMapper mapper;

    /**
     * acquired=true：账本已为本调用登记 IN_FLIGHT（或接管了僵尸行），可以执行工具；
     * acquired=false：existing 为拦截本调用的既有行，由装饰器按状态处理
     * （SUCCESS→返回缓存，UNKNOWN/IN_FLIGHT→对应提示）。
     */
    public record BeginResult(boolean acquired, ToolWriteLedger existing) {
    }

    public BeginResult tryBegin(String idemKey, String toolName) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ToolWriteLedger row = new ToolWriteLedger();
                row.setIdemKey(idemKey);
                row.setToolName(toolName);
                row.setStatus(ToolWriteLedger.STATUS_IN_FLIGHT);
                mapper.insert(row);
                return new BeginResult(true, row);
            }
            catch (DuplicateKeyException e) {
                Optional<ToolWriteLedger> existingOpt = find(idemKey);
                if (existingOpt.isEmpty()) {
                    // 撞键后行又被 release 掉：重试一次登记
                    continue;
                }
                ToolWriteLedger existing = existingOpt.get();
                // UNKNOWN：在有限额度内放行同键重发（僵尸接管只针对 IN_FLIGHT，互不混淆）
                if (ToolWriteLedger.STATUS_UNKNOWN.equals(existing.getStatus())) {
                    if (rearmUnknown(idemKey, toolName)) {
                        return new BeginResult(true, null);
                    }
                    return new BeginResult(false, existing);
                }
                // 僵尸接管交给 takeOver 的 DB 侧条件判断，窗口内的行必然更新 0 行、走拦截
                if (takeOver(idemKey, toolName)) {
                    return new BeginResult(true, null);
                }
                return new BeginResult(false, existing);
            }
        }
        return new BeginResult(false, find(idemKey).orElse(null));
    }

    public Optional<ToolWriteLedger> find(String idemKey) {
        ToolWriteLedger toolWriteLedger = mapper.selectOne(Wrappers.<ToolWriteLedger>lambdaQuery().eq(ToolWriteLedger::getIdemKey, idemKey));
        return Optional.ofNullable(toolWriteLedger);
    }

    /**
     * IN_FLIGHT → SUCCESS 并缓存工具结果；仅在执行权还在手上时成功
     */
    public boolean markSuccess(String idemKey, String response) {
        return transition(idemKey, ToolWriteLedger.STATUS_SUCCESS, response);
    }

    /**
     * IN_FLIGHT → UNKNOWN（写操作遇到结果未知的传输失败）；此后同键调用一律被拦截，等待对账
     */
    public boolean markUnknown(String idemKey) {
        return transition(idemKey, ToolWriteLedger.STATUS_UNKNOWN, null);
    }

    /**
     * 业务拒绝/确证未执行时删除在途行，放行同键的合法重试（如补选地址后重新支付）；
     * 只删 IN_FLIGHT，绝不抹掉 SUCCESS/UNKNOWN 留痕
     */
    public boolean release(String idemKey) {
        return mapper.delete(Wrappers.<ToolWriteLedger>lambdaUpdate()
                .eq(ToolWriteLedger::getIdemKey, idemKey)
                .eq(ToolWriteLedger::getStatus, ToolWriteLedger.STATUS_IN_FLIGHT)) == 1;
    }

    private boolean transition(String idemKey, String toStatus, String response) {
        return mapper.update(null, Wrappers.<ToolWriteLedger>lambdaUpdate()
                .set(ToolWriteLedger::getStatus, toStatus)
                .set(response != null, ToolWriteLedger::getResponse, response)
                .eq(ToolWriteLedger::getIdemKey, idemKey)
                .eq(ToolWriteLedger::getStatus, ToolWriteLedger.STATUS_IN_FLIGHT)) == 1;
    }

    private boolean rearmUnknown(String idemKey, String toolName) {
        // 有界重发：UNKNOWN → IN_FLIGHT 重新接管，条件语句保证并发下最多一个调用者拿到额度
        return mapper.update(null, Wrappers.<ToolWriteLedger>lambdaUpdate()
                .set(ToolWriteLedger::getStatus, ToolWriteLedger.STATUS_IN_FLIGHT)
                .set(ToolWriteLedger::getToolName, toolName)
                .set(ToolWriteLedger::getResponse, (String) null)
                .setSql("retry_count = retry_count + 1, updated_at = NOW()")
                .eq(ToolWriteLedger::getIdemKey, idemKey)
                .eq(ToolWriteLedger::getStatus, ToolWriteLedger.STATUS_UNKNOWN)
                .lt(ToolWriteLedger::getRetryCount, MAX_AMBIGUOUS_RETRY)) == 1;
    }

    private boolean takeOver(String idemKey, String toolName) {
        // 时钟基准统一交给 DB 端 NOW()：MySQL 容器（UTC）与 JVM 本地时间存在时区差，
        // 任何跨端比较 updated_at 都会失真；显式 SET updated_at 防同值更新不触发 ON UPDATE
        return mapper.update(null, Wrappers.<ToolWriteLedger>lambdaUpdate()
                .set(ToolWriteLedger::getToolName, toolName)
                .set(ToolWriteLedger::getResponse, null)
                .setSql("updated_at = NOW()")
                .eq(ToolWriteLedger::getIdemKey, idemKey)
                .eq(ToolWriteLedger::getStatus, ToolWriteLedger.STATUS_IN_FLIGHT)
                .apply("updated_at < NOW() - INTERVAL {0} MINUTE", STALE_IN_FLIGHT.toMinutes())) == 1;
    }
}
