package ai.lifo.spai.shopping.graph;

/**
 * 导购系统意图分类，路由节点的结构化输出目标。
 *
 * @author zhanglifeng
 * @since 2026-11-18
 */
public enum ShoppingIntent {

    /** 商品搜索、推荐、对比、咨询、心愿单、闲聊 */
    GUIDE,

    /** 加购、结算、地址、支付 */
    TRADE,

    /** 订单、物流、退换货、评价、客服工单 */
    AFTER_SALE
}