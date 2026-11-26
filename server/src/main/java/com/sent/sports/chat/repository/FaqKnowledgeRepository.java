package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.FaqKnowledge;

import java.util.List;

/**
 * 机器人知识库仓储端口（V59 / V60）。
 * <p>匹配本身不在这里：仓储只负责「把参与匹配的条目取出来」，排序与权重判定放在
 * {@code FaqMatcher}（纯函数，可单测，换实现不动业务）。</p>
 *
 * <p>读取侧带内存快照（{@link #findAllEnabledOrdered()}），因为机器人每条消息都要匹配一次；
 * 快照<b>没有 TTL</b>，靠写路径显式 {@link #invalidateCache()} 失效——运营改完话术，
 * 下一条消息就必须读到新内容，等过期等于让客户先看几条旧回复。</p>
 */
public interface FaqKnowledgeRepository {

    /** 参与匹配的条目（只含启用），按 id 升序（保证并列时命中结果稳定）。首次访问加载并缓存。 */
    List<FaqKnowledge> findAllEnabledOrdered();

    /**
     * 让快照失效：<b>任何写操作（新增 / 修改 / 启停）之后都必须调用</b>。
     * <p>漏调的后果不是报错，而是「改了没生效」——只有客户投诉才会发现，所以这是写路径的硬约定。</p>
     */
    void invalidateCache();

    /** 命中计数 +1（运营统计用；非关键路径，异常不该影响机器人回复）。 */
    void recordHit(long faqId);
}
