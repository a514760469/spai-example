package ai.lifo.spai.shopping.config;

import ai.lifo.spai.shopping.creds.CredentialInjectingToolCallback;
import ai.lifo.spai.shopping.creds.CredentialStore;
import ai.lifo.spai.shopping.creds.IdempotentToolCallback;
import ai.lifo.spai.shopping.graph.IntentRouterNode;
import ai.lifo.spai.shopping.store.LedgerStore;
import ai.lifo.spai.shopping.tool.LoginTool;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

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
 * 父图与子 Agent 共享同一个 MysqlSaver 实例，子图 checkpoint 落在 {threadId}_subgraph_{agentName} 线程下，
 * 为后续子图内 human-in-the-loop 中断恢复做准备。
 *
 * @author zhanglifeng
 * @since 2026-11-17
 */
@Configuration
@RequiredArgsConstructor
public class AgentConfiguration {


    private static final Set<String> GUIDE_TOOLS = Set.of(
            "searchProducts", "filterProducts", "getFilters", "getSortOptions",
            "getProductDetails", "getProductReviews", "getDeliveryTime",
            "getWishlist", "addToWishlist");

    private static final Set<String> TRADE_TOOLS = Set.of(
            "searchProducts", "getProductDetails", // 交易域补搜索功能
            "addToCart", "getCart", "checkout", "getAddresses", "selectAddress",
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
            - 用户报出手机号后直接调用 loginByPhoneNumber 完成登录；其余工具的凭证由系统自动携带，不要向用户索要 sessionId，也不要在回复中复述任何凭证。
            """;

    private static final String TRADE_PROMPT = """
            你是电商平台的交易助手，负责购物车、结算、地址与支付。
            - 下单链路：addToCart → checkout → getAddresses/selectAddress → initiatePayment → getPaymentStatus。
            - initiatePayment 前必须已完成 selectAddress，服务端会校验，未选地址直接支付会被拒绝。
            - 支付确认由系统接管：调用 initiatePayment 时系统会自动暂停并向用户弹出审批请求，用户批准后才真正执行。
              因此链路走到支付步骤时直接调用 initiatePayment，不要在文本里征求同意，也不要因为等待同意而跳过调用。
            - 每一步失败时向用户说明原因和可选的下一步，不要静默重试。
            - 用户报出手机号后直接调用 loginByPhoneNumber 完成登录；其余工具的凭证由系统自动携带，不要向用户索要 sessionId，也不要在回复中复述任何凭证。
            - 用户只报商品名没有 productId 时，先用 searchProducts 检索确认；多个候选列出来让用户选，不要猜测 productId。
            """;

    private static final String AFTER_SALE_PROMPT = """
            你是电商平台的售后助手，负责订单、物流、退换货、评价与客服工单。
            - 查询类操作直接执行；cancelOrder、returnOrderItem 先用 getOrderDetails 核实状态允许后直接调用，
              系统会自动暂停并向用户弹出审批请求，批准后才真正执行，不要在文本里征求同意。
            - 物流问题可查 getShipments，收货场景用 sendDeliveryOtp。
            - 无法解决的问题创建客服工单，并告知工单号。
            - 用户报出手机号后直接调用 loginByPhoneNumber 完成登录；其余工具的凭证由系统自动携带，不要向用户索要 sessionId，也不要在回复中复述任何凭证。
            """;


    private final DataSource dataSource;

    private final LoginTool loginTool;

    private final CredentialStore credentialStore;

    private final ObjectMapper objectMapper;

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
    public ReactAgent guideAgent(ChatModel chatModel,
                                 ToolCallbackProvider toolCallbackProvider,
                                 LedgerStore ledgerStore,
                                 MysqlSaver mysqlSaver) {
        return domainAgent("guide-agent", GUIDE_PROMPT, GUIDE_TOOLS, chatModel, toolCallbackProvider, mysqlSaver, ledgerStore);
    }

    /**
     * 交易助手 agent，负责购物车、结算、地址与支付
     */
    @Bean
    public ReactAgent tradeAgent(ChatModel chatModel,
                                 ToolCallbackProvider toolCallbackProvider,
                                 LedgerStore ledgerStore,
                                 MysqlSaver mysqlSaver) {
        // 工具调用限制
        ToolCallLimitHook callLimitHook = ToolCallLimitHook.builder()
                .toolName("initiatePayment")
                .runLimit(2)
                .exitBehavior(ToolCallLimitHook.ExitBehavior.END)
                .build();

        return domainAgent("trade-agent", TRADE_PROMPT, TRADE_TOOLS, chatModel, toolCallbackProvider, mysqlSaver,
                ledgerStore,
//                callLimitHook,
                HumanInTheLoopHook.builder()
                        .approvalOn("initiatePayment", "发起支付，将从用户账户扣款，不可逆")
                        .build());
    }

    /**
     * 售后助手 agent，负责订单、物流、退换货、评价与客服工单
     */
    @Bean
    public ReactAgent afterSaleAgent(ChatModel chatModel,
                                     ToolCallbackProvider toolCallbackProvider,
                                     LedgerStore ledgerStore,
                                     MysqlSaver mysqlSaver) {
        return domainAgent("after-sale-agent", AFTER_SALE_PROMPT, AFTER_SALE_TOOLS, chatModel, toolCallbackProvider, mysqlSaver,
                ledgerStore,
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

    private ReactAgent domainAgent(String name,
                                   String systemPrompt,
                                   Set<String> toolNames,
                                   ChatModel chatModel,
                                   ToolCallbackProvider toolCallbackProvider,
                                   MysqlSaver saver,
                                   LedgerStore ledgerStore,
                                   Hook... hooks) {

        List<ToolCallback> tools = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(cb -> toolNames.contains(cb.getToolDefinition().name()))
                .map(cb -> (ToolCallback) new CredentialInjectingToolCallback(cb, credentialStore, objectMapper))
                .map(cb -> (ToolCallback) new IdempotentToolCallback(cb, ledgerStore, objectMapper))
                .collect(Collectors.toCollection(ArrayList::new));

        // 构造 login 的 ToolCallback（Spring AI 标准 API）
        ToolCallback loginCallback = MethodToolCallbackProvider.builder()
                .toolObjects(loginTool).build().getToolCallbacks()[0];

        tools.add(loginCallback);


        // 实测最长一轮 4 次模型调用，8 留一倍余量
        ModelCallLimitHook modelCallLimitHook = ModelCallLimitHook.builder()
                .runLimit(8)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.END)
                .build();

        List<Hook> hookList = new ArrayList<>();
        hookList.add(modelCallLimitHook);
        if (hooks.length > 0) {
            hookList.addAll(Arrays.asList(hooks));
        }

        Builder builder = ReactAgent.builder()
                .name(name)
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(tools)
                .hooks(hookList)
                .saver(saver)
                .enableLogging(true);
        return builder.build();
    }
}