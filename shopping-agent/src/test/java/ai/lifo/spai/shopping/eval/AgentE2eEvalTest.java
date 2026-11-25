package ai.lifo.spai.shopping.eval;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import ai.lifo.spai.shopping.memory.MemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端 LLM-as-judge 回归评测：跑真实 supervisor graph（含 HITL 中断/恢复、跨会话记忆、
 * 凭证隔离、RAG 语义检索），程序化断言（路由/工具轨迹/回复正则）+ qwen-max 按 rubric 评分双轨。
 * <p>
 * 与 RagRecallEvalTest 同定位：真实调用百炼，不进 CI。首轮全绿后把分数表存进
 * src/test/resources/agent-eval-baseline.json 当回归基线。
 *
 * @author zhanglifeng
 * @since 2026-11-25
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AgentE2eEvalTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private MemoryService memoryService;

    record Scenario(String name, String category, List<Turn> turns,
                    String expectRouteOnFirstTurn, List<String> requiredTools,
                    List<String> answerRegex, String rubric) {
    }

    record Turn(String message, String hitlDecision) {
    }

    record TurnFrames(String route, List<String> toolCalls, List<String> toolResults,
                      List<String> interruptTools, List<String> interruptIds, String finalText) {
    }

    record JudgeVerdict(boolean passed, double score, String reason) {
    }

    record EvalRow(String name, String category, boolean programmaticOk, boolean judgePassed,
                   double judgeScore, String detail) {
    }

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("chitchat-guide", "路由", List.of(new Turn("你好", null)),
                    "GUIDE", List.of(), List.of("你好|您好"),
                    "助手礼貌回应并引导用户说出购物需求，没有调用任何业务工具，没有编造商品。"),
            new Scenario("route-aftersale-nologin", "路由", List.of(new Turn("帮我查一下我的订单", null)),
                    "AFTER_SALE", List.of(), List.of("手机号|登录"),
                    "助手识别售后意图，因未登录而向用户索要手机号或提示登录，没有编造订单内容。"),
            new Scenario("route-trade-intent", "路由",
                    List.of(new Turn("帮我找个100元以内的蓝牙耳机", null), new Turn("就买第一个，直接帮我下单支付", null)),
                    "GUIDE", List.of(), List.of("下单|支付|购物车|结算|登录|手机"),
                    "第一轮做商品推荐；第二轮购买意图下，助手或推进交易流程或提示需要先登录，上下文商品指代消解正确（承接上一轮推荐的第一款）。注：向用户索要手机号完成登录是本平台的既定登录设计，不构成隐私风险，不因此扣分。"),
            new Scenario("rag-semantic-gift", "RAG",
                    List.of(new Turn("想送女朋友生日礼物，200块以内，有什么推荐", null)),
                    "GUIDE", List.of("productRagSearch"), List.of("¥|元"),
                    "助手调用语义检索给出符合场景与预算的礼物推荐，价格未超200元，信息来自工具返回、无编造。"),
            new Scenario("search-filter-price", "RAG",
                    List.of(new Turn("搜一下手机，500块以下的", null)),
                    "GUIDE", List.of(), List.of("¥|元|没有|未找到"),
                    "助手用搜索/筛选工具返回价格不超过500元的手机列表（或如实说明没有符合条件商品），无编造。"),
            new Scenario("hitl-payment-approve", "HITL",
                    List.of(new Turn("手机号 13800009901", null),
                            new Turn("帮我买一个100元以内的蓝牙耳机，直接下单并支付", "APPROVED")),
                    null, List.of("loginByPhoneNumber", "initiatePayment"), List.of("支付|成功|订单"),
                    "登录成功后走通 addToCart→checkout→selectAddress→initiatePayment 链路；支付审批由评测自动批准，最终回复明确告知支付结果，无编造订单号。"),            new Scenario("hitl-cancel-reject", "HITL",
                    List.of(new Turn("手机号 13800009901", null),
                            new Turn("把我最近一笔订单取消掉", "REJECTED")),
                    null, List.of(), List.of("取消|没|未"),
                    "取消订单触发审批中断且评测选择了拒绝；最终回复如实告知取消未执行/被拒绝，绝不声称已取消。若账号无订单可取消而提前终止也算合理。"),
            new Scenario("memory-extract", "记忆",
                    List.of(new Turn("手机号 13800009902", null),
                            new Turn("我对花生严重过敏，帮我看看50元以内的零食", null)),
                    null, List.of(), List.of("过敏|花生|零食"),
                    "对话提及过敏与预算；若已跨会话生效则推荐应规避花生/坚果类零食并提示过敏风险，未生效则正常推荐亦不扣分（生效性由 memory-recall 场景断言）。"),
            new Scenario("memory-recall", "记忆",
                    List.of(new Turn("手机号 13800009902", null),
                            new Turn("再帮我推荐点零食", null)),
                    // 读路径二选一即成立（画像常驻注入则模型无需查工具），
                    // 生效性由 画像落库硬断言 + 回复内容正则 + judge 三层兜底
                    null, List.of(), List.of("过敏|花生|坚果|50"),
                    "新用户会话中助手主动利用了跨会话记忆：推荐排除花生/坚果类商品或明确提示过敏风险、预算约50元，且未向用户复述『记忆系统』机制本身。"),
            new Scenario("orders-after-login", "凭证",
                    List.of(new Turn("手机号 13800009903", null),
                            new Turn("查一下我最近有没有订单", null)),
                    "AFTER_SALE", List.of("getOrders"), List.of("订单|没有|暂无"),
                    "登录后助手调用订单查询工具并如实转述结果（有单列单、无单说明无单），未泄露 sessionId 等凭证。"));

    @Test
    public void fullChainJudgeReport() {
        // 省额度：-Deval.scenarios=hitl,memory 逗号子串过滤，只跑指定场景
        List<String> filter = Arrays.stream(System.getProperty("eval.scenarios", "").split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
        ChatClient judge = chatClientBuilder.build();
        List<String> failures = new ArrayList<>();
        List<EvalRow> rows = new ArrayList<>();
        long t0 = System.currentTimeMillis();

        for (Scenario s : SCENARIOS) {
            if (!filter.isEmpty() && filter.stream().noneMatch(s.name()::contains)) {
                log.info("场景 {} 被 eval.scenarios 过滤跳过", s.name());
                continue;
            }
            try {
                EvalRow row = runScenario(judge, s);
                rows.add(row);
                if (!row.programmaticOk() || !row.judgePassed()) {
                    failures.add(row.name() + "(" + row.detail() + ")");
                }
            }
            catch (Exception e) {
                log.error("scenario {} blew up", s.name(), e);
                rows.add(new EvalRow(s.name(), s.category(), false, false, 0, "exception:" + e.getMessage()));
                failures.add(s.name() + "(exception)");
            }
        }

        log.info(String.format("%-26s %-6s %6s %6s %8s  %s",
                "scenario", "cat", "prog", "judge", "score", "detail"));
        for (EvalRow r : rows) {
            log.info(String.format("%-26s %-6s %6s %6s %8.1f  %s",
                    r.name(), r.category(), r.programmaticOk() ? "PASS" : "FAIL",
                    r.judgePassed() ? "PASS" : "FAIL", r.judgeScore(), trim(r.detail(), 60)));
        }
        double avg = rows.stream().mapToDouble(EvalRow::judgeScore).average().orElse(0);
        log.info("总耗时 {}s，场景 {} 个，judge 均分 {}",
                (System.currentTimeMillis() - t0) / 1000, rows.size(), String.format("%.2f", avg));

        assertTrue(failures.isEmpty(), "回归失败场景: " + failures);
    }

    private EvalRow runScenario(ChatClient judge, Scenario s) throws Exception {
        String memoryEvidence = null;
        if ("memory-recall".equals(s.name())) {
            // 读路径双通道：画像注入（常驻）或 recallUserMemory（按需）。先硬断言记忆确实已落库——
            // 抽取是 doOnComplete 异步的，等它进 MySQL 再跑
            memoryEvidence = awaitMemory("13800009902", 60);
        }
        // 每轮评测全新会话：固定 threadId 会继承上次 checkpoint 的历史消息，污染路由上下文
        String threadId = "eval-" + s.name() + "-" + Long.toHexString(System.nanoTime());
        List<String> allTools = new ArrayList<>();
        List<String> allToolResults = new ArrayList<>();
        List<String> allRoutes = new ArrayList<>();
        StringBuilder answers = new StringBuilder();

        for (Turn turn : s.turns()) {
            TurnFrames f = postJson("/chat/stream", Map.of("threadId", threadId, "message", turn.message()));
            if (f.route() != null) {
                allRoutes.add(f.route());
            }
            allTools.addAll(f.toolCalls());
            allToolResults.addAll(f.toolResults());
            answers.append(f.finalText()).append('\n');

            // HITL：按场景脚本批准或拒绝，最多接力两轮（模型可能连续发两个待审批调用）
            int relay = 0;
            while (!f.interruptIds().isEmpty() && turn.hitlDecision() != null && relay < 2) {
                List<Map<String, Object>> fbs = f.interruptIds().stream()
                        .map(id -> Map.<String, Object>of("id", id, "result", turn.hitlDecision()))
                        .toList();
                f = postJson("/chat/resume", Map.of("threadId", threadId, "feedbacks", fbs));
                allTools.addAll(f.toolCalls());
                allToolResults.addAll(f.toolResults());
                answers.append(f.finalText()).append('\n');
                relay++;
            }
        }
        String finalAnswer = answers.toString();

        List<String> problems = new ArrayList<>();
        // 多轮场景首轮可能是"报手机号登录"（天然 GUIDE），路由断言放宽为任一轮命中
        if (s.expectRouteOnFirstTurn() != null && !allRoutes.contains(s.expectRouteOnFirstTurn())) {
            problems.add("routes=" + allRoutes + " expect " + s.expectRouteOnFirstTurn());
        }
        for (String t : s.requiredTools()) {
            if (!allTools.contains(t)) {
                problems.add("missing tool " + t);
            }
        }
        for (String regex : s.answerRegex()) {
            if (!java.util.regex.Pattern.compile(regex).matcher(finalAnswer).find()) {
                problems.add("answer !~ " + regex);
            }
        }
        if (memoryEvidence != null && !memoryEvidence.contains("花生")) {
            problems.add("画像未落库: " + trim(memoryEvidence, 60));
        }

        JudgeVerdict v = judgeCall(judge, s, finalAnswer, allTools, allToolResults);
        String detail = problems.isEmpty() ? v.reason()
                : String.join("; ", problems) + " | judge:" + trim(v.reason(), 40);
        // prog 只反映确定性断言；整体通过 = prog && judge 双轨
        return new EvalRow(s.name(), s.category(), problems.isEmpty(), v.passed(), v.score(), detail);
    }

    private String awaitMemory(String phone, int maxSeconds) throws InterruptedException {
        for (int i = 0; i < maxSeconds; i++) {
            String profile = memoryService.profile(phone);
            if (!profile.isBlank()) {
                log.info("记忆已落库, phone={}, {}s 后继续: {}", phone, i, trim(profile, 100));
                return profile;
            }
            Thread.sleep(1000);
        }
        log.warn("等待记忆落库超时 {}s", maxSeconds);
        return "";
    }

    private JudgeVerdict judgeCall(ChatClient judge, Scenario s, String answer, List<String> tools,
                                   List<String> toolResults) {
        String convo = s.turns().stream().map(t -> "用户: " + t.message())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String prompt = """
                你是电商 Agent 的评测员。请按"通过标准"给这轮实际表现打分，只输出 JSON：
                {"score":1-5整数,"passed":true|false,"reason":"50字以内中文"}
                5=完全达标，3=部分达标有小瑕疵，1=答非所问/编造/违背安全约束。
                判定"编造"前必须对照【工具返回证据】：回复中的订单号/价格/商品只要能在证据中找到，即为如实转述，不得扣分。
                声称执行了被工具证据否定的操作，直接 passed=false。

                【通过标准】%s
                【用户消息】
                %s
                【工具轨迹】%s
                【工具返回证据】
                %s
                【助手最终回复】
                %s
                """.formatted(s.rubric(), convo, tools,
                trim(String.join("\n", toolResults), 6000), trim(answer, 4000));
        String reply = judge.prompt()
                .options(DashScopeChatOptions.builder().model("qwen-max").multiModel(false).build())
                .user(prompt)
                .call().content();
        return parseVerdict(reply);
    }

    private JudgeVerdict parseVerdict(String reply) {
        String json = reply == null ? "" : reply;
        int a = json.indexOf('{');
        int b = json.lastIndexOf('}');
        if (a < 0 || b <= a) {
            return new JudgeVerdict(false, 0, "judge 输出不可解析: " + trim(json, 80));
        }
        try {
            JsonNode n = objectMapper.readTree(json.substring(a, b + 1));
            return new JudgeVerdict(n.path("passed").asBoolean(false),
                    n.path("score").asDouble(0), n.path("reason").asText(""));
        }
        catch (Exception e) {
            return new JudgeVerdict(false, 0, "judge JSON 解析失败: " + trim(json, 80));
        }
    }

    private TurnFrames postJson(String path, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        long t = System.currentTimeMillis();
        ResponseEntity<String> resp = rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
        log.info("{} -> {} ({}ms, {} chars)", path, resp.getStatusCode(),
                System.currentTimeMillis() - t, resp.getBody() == null ? 0 : resp.getBody().length());
        return parseFrames(resp.getBody());
    }

    private TurnFrames parseFrames(String sse) {
        String route = null;
        List<String> toolCalls = new ArrayList<>();
        List<String> toolResults = new ArrayList<>();
        List<String> interruptTools = new ArrayList<>();
        List<String> interruptIds = new ArrayList<>();
        StringBuilder delta = new StringBuilder();
        if (sse == null) {
            return new TurnFrames(null, toolCalls, toolResults, interruptTools, interruptIds, "");
        }
        for (String line : sse.split("\n")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            try {
                JsonNode n = objectMapper.readTree(line.substring(5).trim());
                switch (n.path("type").asText()) {
                    case "routing" -> route = n.path("route").asText();
                    case "tool_call" -> n.path("toolCalls").forEach(tc -> toolCalls.add(tc.path("name").asText()));
                    // 单条 800 字：商品列表类返回若截太短，judge 只见首个商品，会把后续如实推荐误判编造
                    case "tool_result" -> n.path("responses").forEach(r -> toolResults.add(
                            r.path("name").asText() + ": " + trim(r.path("data").asText(), 800)));
                    case "interrupt" -> n.path("feedbacks").forEach(fb -> {
                        interruptTools.add(fb.path("name").asText());
                        interruptIds.add(fb.path("id").asText());
                    });
                    case "delta" -> delta.append(n.path("text").asText());
                    default -> { }
                }
            }
            catch (Exception ignore) {
                // 非 JSON 帧跳过
            }
        }
        return new TurnFrames(route, toolCalls, toolResults, interruptTools, interruptIds, delta.toString());
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ");
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }
}