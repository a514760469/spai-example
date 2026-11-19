package ai.lifo.spai.shopping.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import ai.lifo.spai.shopping.graph.IntentRouterNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;

/**
 * 多 Agent 编排：
 * <pre>
 * START → intent_router ── GUIDE ──────→ guide-agent ──────→ END
 *                       ├─ TRADE ──────→ trade-agent ──────→ END
 *                       └─ AFTER_SALE ─→ after-sale-agent ─→ END
 * </pre>
 * 三个领域子 Agent 以 ReactAgent.asNode(true, false) 作为子图节点嵌入：
 * 子 Agent 可见完整对话历史，但只有最终回复回流父图 messages，工具调用中间推理不污染主上下文。
 * 父图与子 Agent 共享同一个 MysqlSaver 实例，子图 checkpoint 落在 {threadId}_{agentName} 线程下，
 * 为后续子图内 human-in-the-loop 中断恢复做准备。
 *
 * @author zhanglifeng
 * @since 2026-11-17
 */
@Configuration
@RequiredArgsConstructor
public class AgentConfiguration {

    private final DataSource dataSource;

    private static final Set<String> GUIDE_TOOLS = Set.of(
            "searchProducts", "filterProducts", "getFilters", "getSortOptions",
            "getProductDetails", "getProductReviews", "getDeliveryTime",
            "getWishlist", "addToWishlist");

    private static final Set<String> TRADE_TOOLS = Set.of(
            "addToCart", "checkout", "getAddresses", "selectAddress",
            "initiatePayment", "getPaymentStatus");

    private static final Set<String> AFTER_SALE_TOOLS = Set.of(
            "getOrders", "getOrderDetails", "cancelOrder", "returnOrderItem",
            "getShipments", "sendDeliveryOtp",
            "submitProductReview", "submitShipmentReview",
            "getTickets", "getTicketDetails", "createTicket", "addTicketComment");

    private static final String GUIDE_PROMPT = """
            你是电商平台的导购助手，负责商品发现与推荐。
            - 用搜索/筛选工具查找商品，用详情和评价工具核实信息，一切以工具返回为准，不得编造价格、参数、库存。
            - 推荐时给出理由（价格、评分、卖点），多商品对比用 markdown 表格。
            - 需求模糊时先追问预算、用途、偏好，再检索。
            - 用户表达购买意愿时，说明将转入下单流程，并给出所选商品的 productId。
            - 需要 sessionId 的工具若会话中没有 sessionId，先向用户索取，不要编造。
            """;

    private static final String TRADE_PROMPT = """
            你是电商平台的交易助手，负责购物车、结算、地址与支付。
            - 下单链路：addToCart → checkout → getAddresses/selectAddress → initiatePayment → getPaymentStatus。
            - 支付确认由系统接管：调用 initiatePayment 时系统会自动暂停并向用户弹出审批请求，用户批准后才真正执行。
              因此链路走到支付步骤时直接调用 initiatePayment，不要在文本里征求同意，也不要因为等待同意而跳过调用。
            - 每一步失败时向用户说明原因和可选的下一步，不要静默重试。
            - 需要 sessionId 的工具若会话中没有 sessionId，先向用户索取，不要编造。
            """;

    private static final String AFTER_SALE_PROMPT = """
            你是电商平台的售后助手，负责订单、物流、退换货、评价与客服工单。
            - 查询类操作直接执行；cancelOrder、returnOrderItem 先用 getOrderDetails 核实状态允许后直接调用，
              系统会自动暂停并向用户弹出审批请求，批准后才真正执行，不要在文本里征求同意。
            - 物流问题可查 getShipments，收货场景用 sendDeliveryOtp。
            - 无法解决的问题创建客服工单，并告知工单号。
            - 需要 sessionId 的工具若会话中没有 sessionId，先向用户索取，不要编造。
            """;

    /**
     * 父图与子 Agent 共享的 checkpoint saver（同一实例是子图线程命名与恢复的前提）
     */
    @Bean
    public MysqlSaver mysqlSaver() {
        return MysqlSaver.builder().dataSource(dataSource).build();
    }

    /**
     * 路由专用 ChatClient：不挂任何工具，只做意图分类
     */
    @Bean
    public ChatClient routerChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 导购助手 agent，负责商品发现与推荐
     */
    @Bean
    public ReactAgent guideAgent(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider, MysqlSaver mysqlSaver) {
        return domainAgent("guide-agent", GUIDE_PROMPT, GUIDE_TOOLS, chatModel, toolCallbackProvider, mysqlSaver);
    }

    /**
     * 交易助手 agent，负责购物车、结算、地址与支付
     */
    @Bean
    public ReactAgent tradeAgent(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider, MysqlSaver mysqlSaver) {
        return domainAgent("trade-agent", TRADE_PROMPT, TRADE_TOOLS, chatModel, toolCallbackProvider, mysqlSaver,
                HumanInTheLoopHook.builder()
                        .approvalOn("initiatePayment", "发起支付，将从用户账户扣款，不可逆")
                        .build());
    }

    /**
     * 售后助手 agent，负责订单、物流、退换货、评价与客服工单
     */
    @Bean
    public ReactAgent afterSaleAgent(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider, MysqlSaver mysqlSaver) {
        return domainAgent("after-sale-agent", AFTER_SALE_PROMPT, AFTER_SALE_TOOLS, chatModel, toolCallbackProvider, mysqlSaver,
                HumanInTheLoopHook.builder()
                        .approvalOn("cancelOrder", "取消订单，不可逆")
                        .approvalOn("returnOrderItem", "发起退货，将触发退款流程")
                        .build());
    }


    @Bean
    public CompiledGraph shoppingGraph(ReactAgent guideAgent,
                                       ReactAgent tradeAgent,
                                       ReactAgent afterSaleAgent,
                                       ChatClient routerChatClient,
                                       MysqlSaver mysqlSaver) throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> Map.of(
                "messages", new AppendStrategy(),
                "input", new ReplaceStrategy(),
                IntentRouterNode.ROUTE_KEY, new ReplaceStrategy());

        StateGraph graph = new StateGraph("shopping-supervisor", keyStrategyFactory);
        graph.addNode(IntentRouterNode.NODE_NAME, node_async(new IntentRouterNode(routerChatClient)));
        graph.addNode(guideAgent.name(), guideAgent.asNode(true, false));
        graph.addNode(tradeAgent.name(), tradeAgent.asNode(true, false));
        graph.addNode(afterSaleAgent.name(), afterSaleAgent.asNode(true, false));

        graph.addEdge(START, IntentRouterNode.NODE_NAME);
        graph.addConditionalEdges(IntentRouterNode.NODE_NAME,
                edge_async(state -> state.<String>value(IntentRouterNode.ROUTE_KEY).orElse("GUIDE")),
                Map.of("GUIDE", guideAgent.name(),
                        "TRADE", tradeAgent.name(),
                        "AFTER_SALE", afterSaleAgent.name()));
        graph.addEdge(guideAgent.name(), END);
        graph.addEdge(tradeAgent.name(), END);
        graph.addEdge(afterSaleAgent.name(), END);

        return graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(mysqlSaver).build())
                .build());
    }

    private ReactAgent domainAgent(String name, String systemPrompt, Set<String> toolNames,
                                   ChatModel chatModel, ToolCallbackProvider toolCallbackProvider, MysqlSaver saver,
                                   Hook... hooks) {
        List<ToolCallback> tools = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(callback -> toolNames.contains(callback.getToolDefinition().name()))
                .toList();
        Builder builder = ReactAgent.builder()
                .name(name)
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(tools)
                .saver(saver)
                .enableLogging(true);
        if (hooks.length > 0) {
            builder.hooks(hooks);
        }
        return builder.build();
    }
}