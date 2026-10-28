package ai.lifo.agent.tool.serper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Serper（google.serper.dev）的 /search 与 /news 响应。
 * <p>
 * 两个端点返回同一个外壳，只是结果列表一个是 organic、一个是 news，所以放在一起。
 * 所有 record 都标了 ignoreUnknown，Serper 新增字段时不会炸。
 *
 * @author zhanglifeng
 * @since 2026-10-28
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SerperSearchResponse(
        SearchParameters searchParameters,
        List<OrganicResult> organic,
        List<NewsResult> news,
        AnswerBox answerBox,
        KnowledgeGraph knowledgeGraph,
        List<PeopleAlsoAsk> peopleAlsoAsk,
        List<RelatedSearch> relatedSearches,
        Integer credits) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchParameters(String q, String gl, String hl, String type,
                                   Integer num, Integer page, String engine) {}

    /** /search 的网页结果；date、sitelinks 等字段不一定存在 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrganicResult(String title, String link, String snippet,
                                String date, Integer position, List<Sitelink> sitelinks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sitelink(String title, String link) {}

    /** /news 的新闻结果：没有 snippet，只有标题、日期、来源、图片 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewsResult(String title, String link, String date,
                             String source, String imageUrl) {}

    /** 部分查询 Google 直接给答案，可能是 answer 也可能是 snippet */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnswerBox(String title, String answer, String snippet, String link) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KnowledgeGraph(String title, String type, String description,
                                 String descriptionLink, String descriptionSource) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeopleAlsoAsk(String question, String snippet, String link, String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelatedSearch(String query) {}
}
