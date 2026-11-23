package ai.lifo.spai.shopping.creds;

import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Credential store.
 * 存储用户凭证信息，提供保存、查询、删除凭证的功能。
 *
 * @author zhanglifeng
 * @since 2026-11-19
 */
@Component
public class CredentialStore {

    private static final String KEY = "session";

    private final Store store;

    public CredentialStore(Store store) {
        this.store = store;
    }

    public record Credential(String sessionId, String phoneNumber) {}

    public void save(String threadId, String sessionId, String phoneNumber, long expiresAtMillis) {
        long now = System.currentTimeMillis();
        StoreItem item = new StoreItem(
                List.of("creds", rootThreadId(threadId)), KEY,
                Map.of("sessionId", sessionId,
                        "phoneNumber", phoneNumber,
                        "expiresAt", expiresAtMillis),
                now, now);

        store.putItem(item);
    }

    public Optional<Credential> find(String threadId) {
        return store.getItem(List.of("creds", rootThreadId(threadId)), KEY)
                .map(StoreItem::getValue)
                .filter(v -> ((Number) v.get("expiresAt")).longValue() > System.currentTimeMillis())
                .map(v -> new Credential((String) v.get("sessionId"), (String) v.get("phoneNumber")));
    }

    public void remove(String threadId) {
        store.deleteItem(List.of("creds", rootThreadId(threadId)), KEY);
    }

    /**
     * 子 agent 的 threadId 形如 {父id}_subgraph_{agent名}（ReactAgent 共享 saver 时拼接），
     * 凭证按父 threadId 存储，保证一次会话内跨子 agent 共享
     * @param threadId
     * @return
     */
    static String rootThreadId(String threadId) {
        int i = threadId.indexOf("_subgraph_");
        return i > 0 ? threadId.substring(0, i) : threadId;
    }

}
