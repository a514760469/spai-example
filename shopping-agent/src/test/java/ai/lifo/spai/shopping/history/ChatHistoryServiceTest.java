package ai.lifo.spai.shopping.history;

import ai.lifo.spai.shopping.mapper.GraphThreadMapper;
import ai.lifo.spai.shopping.mapper.GraphThreadSummary;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {

    private CompiledGraph shoppingGraph;
    private GraphThreadMapper graphThreadMapper;
    private ChatHistoryService service;

    @BeforeEach
    void setUp() {
        shoppingGraph = mock(CompiledGraph.class);
        graphThreadMapper = mock(GraphThreadMapper.class);
        service = new ChatHistoryService(shoppingGraph, graphThreadMapper);
    }

    @Test
    void listThreadsUsesFirstUserMessageAsTitle() {
        GraphThreadSummary row = new GraphThreadSummary();
        row.setThreadName("thread-1");
        row.setLastActive(1764072000L);
        when(graphThreadMapper.selectParentThreads()).thenReturn(List.of(row));
        stubMessages(new UserMessage("帮我买100元蓝牙耳机"),
                AssistantMessage.builder().content("推荐 XT80").build());

        List<ChatHistoryService.ThreadSummary> threads = service.listThreads();

        assertEquals(1, threads.size());
        assertEquals("thread-1", threads.get(0).threadId());
        assertEquals("帮我买100元蓝牙耳机", threads.get(0).title());
        assertEquals(1764072000L, threads.get(0).lastActive());
    }

    @Test
    void historyExcludesBlankAssistantAndToolMessages() {
        stubMessages(new UserMessage("支付"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "initiatePayment", "{}")))
                        .build(),
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "initiatePayment", "SUCCESS")))
                        .build(),
                AssistantMessage.builder().content("支付成功").build());

        List<ChatHistoryService.HistoryMessage> history = service.history("thread-1");

        assertEquals(List.of(new ChatHistoryService.HistoryMessage("user", "支付"),
                new ChatHistoryService.HistoryMessage("assistant", "支付成功")), history);
    }

    @Test
    void deleteHistoryEscapesLikeWildcardsForChildThreads() {
        when(graphThreadMapper.deleteThreads("thread_1", "thread\\_1\\_subgraph\\_%")).thenReturn(2);

        assertTrue(service.deleteHistory("thread_1"));

        verify(graphThreadMapper).deleteCheckpoints("thread_1", "thread\\_1\\_subgraph\\_%");
        verify(graphThreadMapper).deleteThreads("thread_1", "thread\\_1\\_subgraph\\_%");
    }

    @Test
    void deleteHistoryReturnsFalseWhenNoThreadMatches() {
        when(graphThreadMapper.deleteThreads(any(), any())).thenReturn(0);

        assertFalse(service.deleteHistory("missing"));
    }

    @Test
    void deleteHistoryRejectsBlankThreadId() {
        assertFalse(service.deleteHistory(" "));
    }

    private void stubMessages(Object... messages) {
        StateSnapshot snapshot = mock(StateSnapshot.class);
        when(snapshot.state()).thenReturn(new OverAllState(Map.of("messages", List.of(messages))));
        when(shoppingGraph.stateOf(any(RunnableConfig.class))).thenReturn(Optional.of(snapshot));
    }
}
