package ai.lifo.spai.shopping.creds;

import ai.lifo.spai.shopping.mapper.ToolWriteLedger;
import ai.lifo.spai.shopping.store.LedgerStore;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static ai.lifo.spai.shopping.creds.CredentialStore.rootThreadId;

/**
 * 幂等调用装饰器
 * 调用链：AgentToolNode → IdempotentToolCallback（新）→ CredentialInjectingToolCallback（现有）→ SyncMcpToolCallback → MCP。
 *
 * @author zhanglifeng
 * @since 2026-11-21
 */
@Slf4j
public class IdempotentToolCallback implements StateAwareToolCallback {

    /** 交易写集 v1：双扣款/双下单风险项，含 HITL 覆盖的两笔（账本是第二道闸） */
    private static final Set<String> WRITE_TOOLS = Set.of(
            "addToCart", "checkout", "initiatePayment", "cancelOrder", "returnOrderItem");


    /** UNKNOWN 且重发额度已用尽时的拦截文案（首次超时不透传此文案，而是异常直返模型） */
    private static final String UNKNOWN_HINT = """
        {"status":"UNKNOWN","message":"上次 %s 调用超时且结果未知，同参数重发机会已用尽。请先调用查询工具对账（支付类超时 paymentId 已丢失，用 getPaymentsByCheckout 按 checkoutId 查流水；或 getOrders/getPaymentStatus）：若已执行请告知用户实际状态；若确认未执行，请更换参数重新发起或转人工处理，不要再同参重试。"}
        """;

    private final ToolCallback delegate;
    private final LedgerStore ledger;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final boolean isWrite;

    public IdempotentToolCallback(ToolCallback delegate, LedgerStore ledger, ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.ledger = ledger;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.isWrite = WRITE_TOOLS.contains(delegate.getToolDefinition().name());
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 耗时以真实执行为准；账本拦截/回放路径不触网，单独用 ledger.events 计数
        String tool = getToolDefinition().name();
        long start = System.nanoTime();
        String outcome = "error";
        try {
            String result = doCall(toolInput, toolContext);
            outcome = "success";
            return result;
        }
        finally {
            meterRegistry.timer("shopping.tool.duration", "tool", tool, "outcome", outcome)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private String doCall(String toolInput, ToolContext toolContext) {
        log.info("调用工具：{} {}", getToolDefinition().name(), toolInput);
        if (!isWrite) {
            // 读工具：本次不分级重试，先只做写保护
            return delegate.call(toolInput, toolContext);
        }

        RunnableConfig config = (RunnableConfig) toolContext.getContext()
                .get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);

        String rootThread = rootThreadId(config.threadId().orElseThrow());   // 截 _subgraph_ 前缀
        String key = idemKey(rootThread, toolInput);
        log.info("创建幂等键成功：{}", key);

        LedgerStore.BeginResult begin = ledger.tryBegin(key, getToolDefinition().name());
        if (!begin.acquired()) {
            String blockedResult = blocked(begin.existing());
            log.info("幂等拦截：{} key={} existingStatus={} 返回={}",
                    getToolDefinition().name(), key,
                    begin.existing() == null ? null : begin.existing().getStatus(), blockedResult);
            ledger("blocked_" + (begin.existing() == null ? "unknown_hint"
                    : begin.existing().getStatus().toLowerCase()));
            return blockedResult;
        }

        try {
            String result = delegate.call(toolInput, toolContext);
            if (isRejection(result)) {
                // 确定性拒绝，删掉数据放行
                ledger.release(key);
                ledger("released_reject");
            }
            else {
                ledger.markSuccess(key, result);
                ledger("success");
            }
            return result;
        }
        catch (ToolExecutionException e) {
            log.error("工具调用异常 ToolExecutionException：", e);
            // 实测：spring-ai 把传输层超时（TimeoutException）也包装成 ToolExecutionException，
            // 不能一律当"确定性业务拒绝"。本 server 的拒绝走普通返回 status=error，由 isRejection 处理。
            if (provedNotSent(e)) {
                ledger.release(key);
                ledger("released_connfail");
            }
            else {
                ledger.markUnknown(key);
                ledger("marked_unknown");
            }
            throw e;
        }
        catch (Exception e) {
            log.error("工具调用异常 Exception：", e);
            // ConnectException 类：确证没到达
            if (provedNotSent(e)) {
                ledger.release(key);
                ledger("released_connfail");
                // 交给模型，它重试时同键可重新登记
                throw e;
            }
            // 结果未知：登记 UNKNOWN 后把异常照常透传给模型——
            // 同键重试会拿到一次有界重发额度（LedgerStore.rearmUnknown）；
            // 再次超时则额度用尽，同键走 blocked() 强制对账
            ledger.markUnknown(key);
            ledger("marked_unknown");
            throw new ToolExecutionException(getToolDefinition(), e);
        }
    }

    private void ledger(String event) {
        meterRegistry.counter("shopping.ledger.events",
                "tool", getToolDefinition().name(), "event", event).increment();
    }

    /**
     * 判断工具调用结果是否确定性拒绝。
     * 拒绝载荷格式：{"status":"error","message":"..."}
     */
    private boolean isRejection(String result) {
        try {
            String status = objectMapper.readValue(result, StatusProbe.class).status();
            return "error".equals(status);
        }
        catch (Exception e) {
            log.error("结果解析异常：", e);
            // 解析不了就不是拒绝载荷，照常缓存
            return false;
        }
    }

    private String idemKey(String rootThread, String toolInput) {
        try {
            ObjectNode args = (ObjectNode) objectMapper.readTree(StringUtils.hasText(toolInput) ? toolInput : "{}");
            args.remove("sessionId");

            ObjectNode sorted = objectMapper.createObjectNode();
            args.propertyStream().sorted(Map.Entry.comparingByKey()).forEach(e -> sorted.set(e.getKey(), e.getValue()));

            String raw = rootThread + "|" + getToolDefinition().name() + "|" + objectMapper.writeValueAsString(sorted);
            log.info("生成的幂等键 raw：{}", raw);

            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e) {
            log.error("幂等键 idemKey 创建异常：", e);
            throw new ToolExecutionException(getToolDefinition(), e);
        }
    }

    private String blocked(ToolWriteLedger existing) {
        if (existing == null) return UNKNOWN_HINT.formatted(getToolDefinition().name());
        return switch (existing.getStatus()) {
            case ToolWriteLedger.STATUS_SUCCESS -> existing.getResponse();   // 原结果回放，模型无感
            case ToolWriteLedger.STATUS_UNKNOWN -> UNKNOWN_HINT.formatted(getToolDefinition().name());
            default -> """
                {"status":"IN_FLIGHT","message":"同参数请求正在执行中，请勿重复发起。"}""";
        };
    }

    /** 沿 cause 链找"确证未到达"：连接被拒/DNS/无路由——重试这些不会造成双重执行 */
    private boolean provedNotSent(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof NoRouteToHostException) {
                return true;
            }
        }
        // 其余一律按"结果未知"保守处理
        return false;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StatusProbe(String status) { }

}
