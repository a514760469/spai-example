package ai.lifo.spai.shopping.history;

import ai.lifo.spai.shopping.mapper.GraphThreadMapper;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史查询与清理：线程元数据走 MyBatis，消息内容通过 Graph checkpoint 还原。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final String SUBGRAPH_SUFFIX = "_subgraph_";

    private static final int TITLE_MAX_LENGTH = 30;

    private final CompiledGraph shoppingGraph;

    private final GraphThreadMapper graphThreadMapper;

    public record ThreadSummary(String threadId, String title, long lastActive) {
    }

    public record HistoryMessage(String role, String text) {
    }

    /**
     * 父线程列表：子 Agent 线程是内部实现，不展示；标题取首条用户消息。
     */
    public List<ThreadSummary> listThreads() {
        return graphThreadMapper.selectParentThreads()
                .stream()
                .map(row -> new ThreadSummary(row.getThreadName(), title(row.getThreadName()), row.getLastActive()))
                .toList();
    }

    /**
     * 会话历史：父图最新 checkpoint 的 messages 即“用户输入 + 子 agent 最终回复”序列，
     * 与聊天 UI 展示粒度一致（工具中间过程在子图 checkpoint，不回看）。
     */
    public List<HistoryMessage> history(String threadId) {
        return shoppingGraph.stateOf(RunnableConfig.builder().threadId(threadId).build())
                .map(snapshot -> {
                    List<?> messages = snapshot.state().<List<?>>value("messages").orElse(List.of());
                    List<HistoryMessage> result = new ArrayList<>();
                    for (Object message : messages) {
                        if (message instanceof UserMessage user) {
                            result.add(new HistoryMessage("user", user.getText()));
                        }
                        else if (message instanceof AssistantMessage assistant
                                && StringUtils.hasText(assistant.getText())) {
                            result.add(new HistoryMessage("assistant", assistant.getText()));
                        }
                    }
                    return result;
                })
                .orElse(List.of());
    }

    /**
     * 删除一个父会话及其所有子 Agent 会话；checkpoint 随线程一起清理。
     */
    @Transactional
    public boolean deleteHistory(String threadId) {
        if (!StringUtils.hasText(threadId)) {
            return false;
        }
        String childPattern = childThreadPattern(threadId);
        graphThreadMapper.deleteCheckpoints(threadId, childPattern);
        int deletedThreads = graphThreadMapper.deleteThreads(threadId, childPattern);
        log.info("删除会话历史, thread={}, childPattern={}, threads={}", threadId, childPattern, deletedThreads);
        return deletedThreads > 0;
    }

    private String title(String threadId) {
        try {
            return history(threadId).stream()
                    .filter(message -> "user".equals(message.role()) && StringUtils.hasText(message.text()))
                    .findFirst()
                    .map(HistoryMessage::text)
                    .map(ChatHistoryService::truncateTitle)
                    .orElseGet(() -> fallbackTitle(threadId));
        }
        catch (Exception e) {
            log.warn("读取会话标题失败, thread={}: {}", threadId, e.getMessage());
            return fallbackTitle(threadId);
        }
    }

    private static String fallbackTitle(String threadId) {
        return "会话 " + threadId.substring(0, Math.min(8, threadId.length()));
    }

    private static String truncateTitle(String text) {
        return text.length() <= TITLE_MAX_LENGTH ? text : text.substring(0, TITLE_MAX_LENGTH) + "…";
    }

    private static String childThreadPattern(String threadId) {
        return escapeLike(threadId) + SUBGRAPH_SUFFIX.replace("_", "\\_") + "%";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
