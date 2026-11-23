package ai.lifo.spai.shopping.store;

import ai.lifo.spai.shopping.mapper.ToolWriteLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LedgerStore 集成验证：@SpringBootTest 走真实 MySQL（5.7），全部行为断言基于
 * MyBatis-Plus 实际生成的 SQL 与唯一索引/条件更新语义，数据保留在 tool_write_ledger 便于事后查看。
 * 每个用例独立 idem_key（runId + 用例名），重复运行互不干扰。
 */
@SpringBootTest
class LedgerStoreTest {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private LedgerStore ledgerStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String key(String testCase) {
        return "ledger-test/" + RUN_ID + "/" + testCase;
    }

    @Test
    void acquire_then_same_key_blocked_until_terminal() {
        String k = key("inflight");
        LedgerStore.BeginResult first = ledgerStore.tryBegin(k, "initiatePayment");
        assertTrue(first.acquired(), "首次登记应拿到执行权");

        LedgerStore.BeginResult second = ledgerStore.tryBegin(k, "initiatePayment");
        assertFalse(second.acquired(), "IN_FLIGHT 期间的同键调用必须被拦截");
        assertEquals(ToolWriteLedger.STATUS_IN_FLIGHT, second.existing().getStatus());
    }

    @Test
    void success_caches_response_and_blocks_retry() {
        String k = key("success");
        assertTrue(ledgerStore.tryBegin(k, "initiatePayment").acquired());
        assertTrue(ledgerStore.markSuccess(k, "{\"paymentId\":\"p1\"}"));

        LedgerStore.BeginResult retry = ledgerStore.tryBegin(k, "initiatePayment");
        assertFalse(retry.acquired());
        assertEquals(ToolWriteLedger.STATUS_SUCCESS, retry.existing().getStatus());
        assertEquals("{\"paymentId\":\"p1\"}", retry.existing().getResponse());

        // 终态不可再流转：markSuccess/markUnknown 只在 IN_FLIGHT 上成立
        assertFalse(ledgerStore.markUnknown(k), "SUCCESS 之后不得被改写为 UNKNOWN");
        // release 只删 IN_FLIGHT，绝不抹掉 SUCCESS 留痕
        assertFalse(ledgerStore.release(k));
        assertNotNull(ledgerStore.find(k).orElse(null));
    }

    @Test
    void unknown_allows_single_bounded_rearm_then_blocks() {
        String k = key("unknown");
        assertTrue(ledgerStore.tryBegin(k, "initiatePayment").acquired());
        assertTrue(ledgerStore.markUnknown(k));

        LedgerStore.BeginResult rearm = ledgerStore.tryBegin(k, "initiatePayment");
        assertTrue(rearm.acquired(), "UNKNOWN 应放行一次有界重发，化解超时死锁");
        assertEquals(ToolWriteLedger.STATUS_IN_FLIGHT, ledgerStore.find(k).orElseThrow().getStatus());
        assertEquals(1, ledgerStore.find(k).orElseThrow().getRetryCount().intValue(), "重发应消耗额度并回到 IN_FLIGHT");

        // 重发再次超时 → 又标 UNKNOWN → 额度已用尽，必须永久拦截等待对账
        assertTrue(ledgerStore.markUnknown(k));
        LedgerStore.BeginResult exhausted = ledgerStore.tryBegin(k, "initiatePayment");
        assertFalse(exhausted.acquired(), "重发额度用尽后同键不得再自动执行");
        assertEquals(ToolWriteLedger.STATUS_UNKNOWN, exhausted.existing().getStatus());
    }

    @Test
    void business_rejection_releases_key_for_legit_retry() {
        String k = key("release");
        assertTrue(ledgerStore.tryBegin(k, "initiatePayment").acquired());
        assertTrue(ledgerStore.release(k), "业务拒绝应删除在途行");

        // 补选地址后同键重试：必须能重新拿到执行权
        assertTrue(ledgerStore.tryBegin(k, "initiatePayment").acquired());
    }

    @Test
    void stale_inflight_is_taken_over() {
        String k = key("stale");
        assertTrue(ledgerStore.tryBegin(k, "initiatePayment").acquired());
        // 模拟进程崩溃：时间旅行把行 aging 到僵尸窗口之外
        jdbcTemplate.update("UPDATE tool_write_ledger SET updated_at = NOW() - INTERVAL 30 MINUTE WHERE idem_key = ?", k);

        LedgerStore.BeginResult retry = ledgerStore.tryBegin(k, "initiatePayment");
        assertTrue(retry.acquired(), "超过 STALE_IN_FLIGHT 窗口的僵尸行应被接管");
        assertNull(ledgerStore.find(k).orElseThrow().getResponse(), "接管应清空旧 response");
    }

    @Test
    void fresh_inflight_not_taken_over() {
        String k = key("fresh");
        assertTrue(ledgerStore.tryBegin(k, "initiatePayment").acquired());
        assertFalse(ledgerStore.tryBegin(k, "initiatePayment").acquired(), "窗口内的 IN_FLIGHT 不可被接管");
    }
}
