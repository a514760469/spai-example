package ai.lifo.spai.shopping.config;

import ai.lifo.spai.shopping.store.MysqlStore;
import com.alibaba.cloud.ai.graph.store.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * @author zhanglifeng
 * @since 2026-11-18
 */
@Configuration
@RequiredArgsConstructor
public class StoreConfiguration {

    /**
     * 长期记忆存储
     */
    @Bean
    public Store databaseStore(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return new MysqlStore(dataSource, jdbcTemplate);
    }
}
