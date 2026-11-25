package ai.lifo.spai.shopping.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 长期记忆核心行为：写入/语义召回/用户隔离/内容去重/画像摘要/删除同步向量副本。
 * 每次运行用 RUN_ID 隔离手机号，结束清理，不污染真实记忆。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@SpringBootTest
class MemoryServiceTest {

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 6);

    private static final String PHONE = "199" + Integer.toHexString(RUN_ID.hashCode() & 0x7fffffff);

    private static final String OTHER_PHONE = "255" + Integer.toHexString(RUN_ID.hashCode() & 0x7fffffff);

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private UserMemoryStore store;

    @BeforeEach
    void seed() {
        memoryService.deleteByUser(PHONE);
        memoryService.deleteByUser(OTHER_PHONE);
    }

    @AfterEach
    void cleanup() {
        memoryService.deleteByUser(PHONE);
        memoryService.deleteByUser(OTHER_PHONE);
    }

    @Test
    void write_recall_isolate_dedupe_profile() {
        int saved = memoryService.write(PHONE, "test-thread-" + RUN_ID, List.of(
                new MemoryService.MemoryItem("PREFERENCE", "购物预算通常在1000元以内，对价格敏感"),
                new MemoryService.MemoryItem("PROFILE", "对花生过敏，购买食品时关注配料表"),
                new MemoryService.MemoryItem("PROFILE", "经常给爱喝茶的岳父买礼物")));
        assertEquals(3, saved, "首次写入应全部落库");

        // 语义召回：query 不包含"岳父"以外的任何子串命中条件，走的是 embedding 相似度
        List<MemoryService.MemoryHit> hits = memoryService.recall(PHONE, "给老丈人送点茶叶", 3);
        assertFalse(hits.isEmpty(), "语义召回不应为空");
        assertTrue(hits.stream().anyMatch(h -> h.content().contains("岳父")),
                "与'给老丈人送茶叶'最相关的应是岳父送礼记忆，实际: " + hits);

        // 用户隔离：另一个手机号查不到任何记忆
        assertTrue(memoryService.recall(OTHER_PHONE, "给老丈人送点茶叶", 3).isEmpty(),
                "记忆必须按用户硬隔离");

        // 内容去重：同一条事实重复写入被唯一键拦截
        int again = memoryService.write(PHONE, "test-thread-" + RUN_ID,
                List.of(new MemoryService.MemoryItem("PREFERENCE", "购物预算通常在1000元以内，对价格敏感")));
        assertEquals(0, again, "完全重复的记忆不应重复入库");
        assertEquals(3, store.countByUser(PHONE));

        // 画像摘要：包含全部事实，按行前缀格式化
        String profile = memoryService.profile(PHONE);
        assertTrue(profile.contains("花生"), "画像应包含过敏信息: " + profile);
        assertTrue(profile.lines().count() == 3, "画像行数应等于记忆条数: " + profile);

        // 删除同步：表与向量副本同时清空
        assertEquals(3, memoryService.deleteByUser(PHONE));
        assertTrue(memoryService.recall(PHONE, "给老丈人送点茶叶", 3).isEmpty(), "删除后向量副本不应残留");
    }
}