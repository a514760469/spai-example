package ai.lifo.agent.support;

import com.alibaba.cloud.ai.dashscope.common.DashScopeException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把上游（百炼 / DashScope）在流式输出中途抛的异常，翻译成用户能看懂的一句话。
 *
 * <p>典型场景：模型输出命中阿里云内容安全审核，DashScope 会在 SSE 流中途抛
 * {@link DashScopeException}，{@code getCode()} 为 {@code DataInspectionFailed}：
 * <pre>
 * [DataInspectionFailed] &lt;400&gt; InternalError.Algo.DataInspectionFailed:
 * Output data may contain inappropriate content. (requestId: xxx)
 * </pre>
 *
 * <p>链路上各方对它的处理方式是：Spring AI 的 {@code MessageAggregator} 只打一行
 * {@code Aggregation Error} 日志；Spring AI Alibaba Graph 把节点异常包成
 * {@code GraphResponse.error} 后继续沿流上抛。于是前端看到的现象是
 * 「输出到一半突然断了，没有任何提示」。这个类负责补上那句话。
 *
 * @author zhanglifeng
 * @since 2026-10-28
 */
@Slf4j
public final class AgentStreamErrors {

	/** 百炼内容安全（绿网）拦截，输入侧 / 输出侧都会用它 */
	public static final String CODE_DATA_INSPECTION = "DataInspectionFailed";

	private static final Pattern REQUEST_ID = Pattern.compile("requestId[:：]\\s*([0-9a-fA-F-]{16,})");

	private AgentStreamErrors() {
	}

	/**
	 * 是否命中上游内容安全审核。
	 */
	public static boolean isDataInspectionFailed(Throwable error) {
		Throwable cause = rootCause(error);
		if (cause instanceof DashScopeException dash && dash.getCode() != null) {
			return dash.getCode().startsWith(CODE_DATA_INSPECTION);
		}
		String message = cause.getMessage();
		return message != null && message.contains(CODE_DATA_INSPECTION);
	}

	/**
	 * 给用户看的一句话。返回 {@code null} 表示「这类异常我不认识，别拦，照原样抛上去」。
	 */
	public static String describe(Throwable error) {
		Throwable cause = rootCause(error);
		String message = cause.getMessage() == null ? "" : cause.getMessage();

		if (isDataInspectionFailed(cause)) {
			return "\n\n[本轮回答被阿里云内容安全审核拦截（输出侧），没有生成完整内容。"
					+ "可以换个问法再试一次；如果反复触发，检查是否有检索到的原文被原样复述。requestId=%s]"
					.formatted(requestId(cause));
		}
		if (message.contains("Throttling") || message.contains("RateLimit") || message.contains("limit_requests")) {
			return "\n\n[请求过于频繁被上游限流，请稍后重试。requestId=%s]".formatted(requestId(cause));
		}
		if (message.contains("Arrearage") || message.contains("AllocationQuota")) {
			return "\n\n[模型额度已用尽或账号欠费，请去百炼控制台确认。]";
		}
		if (cause instanceof TimeoutException || message.contains("timeout") || message.contains("Timeout")) {
			return "\n\n[上游响应超时，请重试或缩短问题。]";
		}
		return null;
	}

	/**
	 * 从异常信息里抠出百炼的 requestId，方便提工单。
	 */
	public static String requestId(Throwable error) {
		Throwable cause = rootCause(error);
		String message = cause.getMessage();
		if (message == null) {
			return "";
		}
		Matcher matcher = REQUEST_ID.matcher(message);
		return matcher.find() ? matcher.group(1) : "";
	}

	/**
	 * 兜住流里的异常：{@code fallbackFactory} 返回非 null 就换成这条兜底数据，返回 null 则原样抛。
	 *
	 * <pre>{@code
	 * return AgentStreamErrors.guard(
	 *         agent.stream(question).map(this::render),
	 *         error -> AgentStreamErrors.describe(error));
	 * }</pre>
	 */
	public static <T> Flux<T> guard(Flux<T> source, Function<Throwable, T> fallbackFactory) {
		return source.onErrorResume(error -> {
			T fallback = fallbackFactory.apply(error);
			if (fallback == null) {
				return Flux.error(error);
			}
			log.warn("流式输出中断，已用兜底文案收尾：requestId={} msg={}",
					requestId(error), rootCause(error).getMessage());
			return Flux.just(fallback);
		});
	}

	private static Throwable rootCause(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}

}
