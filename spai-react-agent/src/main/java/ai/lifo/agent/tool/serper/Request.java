package ai.lifo.agent.tool.serper;

/**
 * @param query      搜索词，尽量写完整、可独立成立的查询
 * @param maxResults 返回条数，1~10，默认 5
 * @param type       检索类型：search（网页，默认）/ news（新闻）
 * @param timeRange  时间范围：day / week / month / year，可不填
 */
public record Request(String query, Integer maxResults, String type, String timeRange) {
}
