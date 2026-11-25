package ai.lifo.spai.shopping.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Graph checkpoint 线程数据访问层。
 * 子 Agent 的线程名固定为 {parentThreadName}_subgraph_{agentName}，删除会话时需要同时清理父、子线程；
 * checkpoint 先显式删除，避免依赖数据库级联配置。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Mapper
public interface GraphThreadMapper {

    @Select("""
            SELECT t.thread_name AS threadName,
                   CAST(UNIX_TIMESTAMP(MAX(c.saved_at)) AS UNSIGNED) AS lastActive
            FROM GRAPH_THREAD t
            LEFT JOIN GRAPH_CHECKPOINT c ON c.thread_id = t.thread_id
            WHERE t.is_released = FALSE
              AND t.thread_name NOT LIKE '%\\_subgraph\\_%'
            GROUP BY t.thread_name
            ORDER BY lastActive DESC
            """)
    List<GraphThreadSummary> selectParentThreads();

    @Delete("""
            DELETE c FROM GRAPH_CHECKPOINT c
            JOIN GRAPH_THREAD t ON t.thread_id = c.thread_id
            WHERE t.thread_name = #{threadName}
               OR t.thread_name LIKE #{childPattern}
            """)
    int deleteCheckpoints(@Param("threadName") String threadName,
                          @Param("childPattern") String childPattern);

    @Delete("""
            DELETE FROM GRAPH_THREAD
            WHERE thread_name = #{threadName}
               OR thread_name LIKE #{childPattern}
            """)
    int deleteThreads(@Param("threadName") String threadName,
                      @Param("childPattern") String childPattern);
}
