package ai.lifo.spai.shopping.creds;

import ai.lifo.spai.shopping.store.MysqlStore;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CredentialStore 集成验证：@SpringBootTest 走 application.yaml 的真实数据源（MySQL 5.7），
 * 凭证直接落在 spring_ai_store 表并保留，便于事后查看。
 * 每个测试用随机 UUID 作 threadId，namespace 为 creds/{threadId}，
 * 天然隔离、重复运行互不干扰、不依赖执行顺序。
 */
@SpringBootTest
class CredentialStoreTest {

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private Store store;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String newThreadId() {
        return UUID.randomUUID().toString();
    }

    private static long future() {
        return System.currentTimeMillis() + 3_600_000;
    }

    @Test
    void saveAndFindRoundTrip() {
        String threadId = newThreadId();
        String sessionId = UUID.randomUUID().toString();
        credentialStore.save(threadId, sessionId, "13800001111", future());

        CredentialStore.Credential credential = credentialStore.find(threadId).orElseThrow();
        assertEquals(sessionId, credential.sessionId());
        assertEquals("13800001111", credential.phoneNumber());

        StoreItem raw = store.getItem(List.of("creds", threadId), "session").orElseThrow();
        assertEquals(List.of("creds", threadId), raw.getNamespace());
        assertEquals(sessionId, raw.getValue().get("sessionId"));
        assertTrue(((Number) raw.getValue().get("expiresAt")).longValue() > System.currentTimeMillis());
    }

    @Test
    void expiredCredentialIsNotFound() {
        String threadId = newThreadId();
        credentialStore.save(threadId, UUID.randomUUID().toString(), "13800001111",
                System.currentTimeMillis() - 1000);
        assertTrue(credentialStore.find(threadId).isEmpty());

        credentialStore.save(threadId, UUID.randomUUID().toString(), "13800001111", future());
        assertTrue(credentialStore.find(threadId).isPresent());
    }

    @Test
    void reLoginOverwritesSession() {
        String threadId = newThreadId();
        String oldSession = UUID.randomUUID().toString();
        String newSession = UUID.randomUUID().toString();
        credentialStore.save(threadId, oldSession, "13800001111", future());
        credentialStore.save(threadId, newSession, "13800002222", future());

        CredentialStore.Credential credential = credentialStore.find(threadId).orElseThrow();
        assertEquals(newSession, credential.sessionId());
        assertEquals("13800002222", credential.phoneNumber());
    }

    @Test
    void threadsAreIsolated() {
        String threadA = newThreadId();
        String threadB = newThreadId();
        String sessionA = UUID.randomUUID().toString();
        credentialStore.save(threadA, sessionA, "13800001111", future());

        assertEquals(sessionA, credentialStore.find(threadA).orElseThrow().sessionId());
        assertTrue(credentialStore.find(threadB).isEmpty());
    }

    @Test
    void removeDeletesCredentialAndIsIdempotent() {
        String threadId = newThreadId();
        credentialStore.save(threadId, UUID.randomUUID().toString(), "13800001111", future());

        credentialStore.remove(threadId);
        assertTrue(credentialStore.find(threadId).isEmpty());
        assertTrue(store.getItem(List.of("creds", threadId), "session").isEmpty());

        credentialStore.remove(threadId);
    }

    @Test
    void findUnknownThreadReturnsEmpty() {
        assertTrue(credentialStore.find(newThreadId()).isEmpty());
    }

    @Test
    void credentialSurvivesNewStoreInstance() {
        String threadId = newThreadId();
        String sessionId = UUID.randomUUID().toString();
        credentialStore.save(threadId, sessionId, "13800001111", future());

        CredentialStore fresh = new CredentialStore(new MysqlStore(dataSource, jdbcTemplate));
        Optional<CredentialStore.Credential> credential = fresh.find(threadId);
        assertTrue(credential.isPresent());
        assertEquals(sessionId, credential.orElseThrow().sessionId());
    }
}