package ai.lifo.spai.shopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * user_memory 数据访问层：CRUD 走 BaseMapper + LambdaWrapper，无 XML；
 * 建表 DDL 用注解 SQL 保留启动自动建表能力。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {

    @Update("""
            CREATE TABLE IF NOT EXISTS user_memory (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_key VARCHAR(64) NOT NULL,
                memory_type VARCHAR(32) NOT NULL,
                content VARCHAR(512) NOT NULL,
                source_thread VARCHAR(128),
                created_at DATETIME(3) NOT NULL,
                updated_at DATETIME(3) NOT NULL,
                UNIQUE KEY uk_user_content (user_key, content(191)),
                KEY idx_user (user_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
    void createTableIfNotExists();
}