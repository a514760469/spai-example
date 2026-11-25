package ai.lifo.spai.shopping.controller;

import ai.lifo.spai.shopping.history.ChatHistoryService;
import ai.lifo.spai.shopping.memory.MemoryExtractionService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback.FeedbackResult;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private static final String THREAD_ID = "hitl-restart";
    private static final String AGENT_NAME = "trade-agent";
    private static final String CALL_ID = "call-payment-1";
    private static final String ARGUMENTS = "{\"amount\":88}";

    private CompiledGraph parentGraph;
    private CompiledGraph childGraph;
    private ChatController controller;
    private ArgumentCaptor<RunnableConfig> childConfigCaptor;

    @BeforeEach
    void setUp() {
        parentGraph = mock(CompiledGraph.class);
        childGraph = mock(CompiledGraph.class);
        ReactAgent agent = mock(ReactAgent.class);
        StateSnapshot parentSnapshot = mock(StateSnapshot.class);
        StateSnapshot childSnapshot = mock(StateSnapshot.class);
        HumanInTheLoopHook hook = HumanInTheLoopHook.builder()
                .approvalOn("initiatePayment", "发起支付，将从用户账户扣款，不可逆")
                .build();
        hook.setAgentName(AGENT_NAME);

        when(agent.name()).thenReturn(AGENT_NAME);
        when(parentGraph.stateOf(any(RunnableConfig.class))).thenReturn(Optional.of(parentSnapshot));
        when(parentSnapshot.next()).thenReturn(AGENT_NAME);
        when(agent.getAndCompileGraph()).thenReturn(childGraph);
        when(childGraph.stateOf(any(RunnableConfig.class))).thenReturn(Optional.of(childSnapshot));
        when(parentGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());
        childConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);

        controller = new ChatController(parentGraph, new ObjectMapper(),
                mock(MemoryExtractionService.class), new SimpleMeterRegistry(),
                List.of(agent), List.of(hook), mock(ChatHistoryService.class));
    }

    @Test
    void reconstructsApprovedFeedbackFromChildCheckpoint() {
        stubPendingToolCall();

        InterruptionMetadata feedback = resume(new ChatController.Decision(CALL_ID, "APPROVED", null, null));

        assertEquals(THREAD_ID + "_subgraph_" + AGENT_NAME,
                childConfigCaptor.getValue().threadId().orElseThrow());
        ToolFeedback tool = feedback.toolFeedbacks().get(0);
        assertEquals(CALL_ID, tool.getId());
        assertEquals("initiatePayment", tool.getName());
        assertEquals(ARGUMENTS, tool.getArguments());
        assertEquals(FeedbackResult.APPROVED, tool.getResult());
        assertTrue(tool.getDescription().contains("发起支付"));
    }

    @Test
    void reconstructedFeedbackSupportsEditedArguments() {
        stubPendingToolCall();

        InterruptionMetadata feedback = resume(new ChatController.Decision(
                CALL_ID, "EDITED", "{\"amount\":99}", null));

        ToolFeedback tool = feedback.toolFeedbacks().get(0);
        assertEquals(FeedbackResult.EDITED, tool.getResult());
        assertEquals("{\"amount\":99}", tool.getArguments());
    }

    @Test
    void reconstructedFeedbackSupportsRejectionReason() {
        stubPendingToolCall();

        InterruptionMetadata feedback = resume(new ChatController.Decision(
                CALL_ID, "REJECTED", null, "预算不足"));

        ToolFeedback tool = feedback.toolFeedbacks().get(0);
        assertEquals(FeedbackResult.REJECTED, tool.getResult());
        assertEquals("预算不足", tool.getDescription());
    }

    @Test
    void completedCheckpointCannotBeApprovedAgain() {
        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        CALL_ID, "function", "initiatePayment", ARGUMENTS)))
                .build();
        ToolResponseMessage response = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        CALL_ID, "initiatePayment", "{\"status\":\"SUCCESS\"}")))
                .build();
        stubChildState(new OverAllState(Map.of("messages", List.of(assistant, response))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.resume(new ChatController.ResumeRequest(THREAD_ID,
                        List.of(new ChatController.Decision(CALL_ID, "APPROVED", null, null)))).blockLast());

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void missingParentCheckpointReturnsConflict() {
        when(parentGraph.stateOf(any(RunnableConfig.class))).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.resume(new ChatController.ResumeRequest(THREAD_ID,
                        List.of(new ChatController.Decision(CALL_ID, "APPROVED", null, null)))).blockLast());

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private void stubPendingToolCall() {
        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        CALL_ID, "function", "initiatePayment", ARGUMENTS)))
                .build();
        stubChildState(new OverAllState(Map.of("messages", List.of(assistant))));
    }

    private void stubChildState(OverAllState state) {
        StateSnapshot childSnapshot = mock(StateSnapshot.class);
        when(childSnapshot.state()).thenReturn(state);
        when(childGraph.stateOf(childConfigCaptor.capture())).thenReturn(Optional.of(childSnapshot));
    }

    private InterruptionMetadata resume(ChatController.Decision decision) {
        controller.resume(new ChatController.ResumeRequest(THREAD_ID, List.of(decision))).blockLast();
        ArgumentCaptor<RunnableConfig> resumeConfig = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(parentGraph).stream(anyMap(), resumeConfig.capture());
        return (InterruptionMetadata) resumeConfig.getValue()
                .metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY).orElseThrow();
    }
}
