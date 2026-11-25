package ai.lifo.spai.shopping.memory;

import ai.lifo.spai.shopping.mapper.UserMemory;
import ai.lifo.spai.shopping.mapper.UserMemoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆的权威存储（MySQL）。向量索引只是它的检索副本，删改以本表为准。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserMemoryStore {

    private final UserMemoryMapper mapper;

    @PostConstruct
    public void init() {
        log.info("creating table if not exists... ");
        mapper.createTableIfNotExists();
    }

    /** @return 新行主键；content 与既有记忆完全相同时返回 -1（唯一键冲突即语义重复） */
    public long insert(String userKey, String memoryType, String content, String sourceThread) {
        UserMemory row = new UserMemory();
        row.setUserKey(userKey);
        row.setMemoryType(memoryType);
        row.setContent(content);
        row.setSourceThread(sourceThread);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            mapper.insert(row);
            return row.getId();
        }
        catch (DuplicateKeyException e) {
            return -1;
        }
    }

    public List<UserMemory> listByUser(String userKey, int limit) {
        return mapper.selectList(Wrappers.<UserMemory>lambdaQuery()
                .eq(UserMemory::getUserKey, userKey)
                .orderByDesc(UserMemory::getUpdatedAt)
                .last("LIMIT " + limit));
    }

    public int countByUser(String userKey) {
        return mapper.selectCount(Wrappers.<UserMemory>lambdaQuery()
                .eq(UserMemory::getUserKey, userKey)).intValue();
    }

    public List<Long> deleteByUser(String userKey) {
        List<Long> ids = mapper.selectList(Wrappers.<UserMemory>lambdaQuery()
                        .select(UserMemory::getId)
                        .eq(UserMemory::getUserKey, userKey))
                .stream().map(UserMemory::getId).toList();
        mapper.delete(Wrappers.<UserMemory>lambdaQuery().eq(UserMemory::getUserKey, userKey));
        return ids;
    }
}