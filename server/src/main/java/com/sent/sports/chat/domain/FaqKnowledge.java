package com.sent.sports.chat.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器人知识库条目（对应 {@code faq_knowledge}）：纯 POJO，不含持久化与 Spring 依赖。
 *
 * <p>{@code keywords} 是英文逗号分隔的关键词串（如 {@code "发货,物流,快递"}），
 * 一条 FAQ 命中其中任意一个词即算匹配；命中后原文回复给客户。
 * {@code hitCount} 只用于运营排序，不参与匹配。</p>
 *
 * <p>{@code status} 是 V60 起加的启停开关：停用条目仍留在库里（命中次数是运营评估话术的依据），
 * 但不参与匹配——所以查询侧必须在 SQL 里过滤，而不是取出来再在内存里丢。</p>
 */
public class FaqKnowledge {

    private final long id;
    private final String keywords;
    private final String replyContent;
    private final int hitCount;
    private final FaqStatus status;
    private final long updatedAtMillis;

    /** 兼容四参构造：新增的条目默认启用，更新时间未知记 0。 */
    public FaqKnowledge(long id, String keywords, String replyContent, int hitCount) {
        this(id, keywords, replyContent, hitCount, FaqStatus.ENABLED, 0L);
    }

    public FaqKnowledge(long id, String keywords, String replyContent, int hitCount,
                        FaqStatus status, long updatedAtMillis) {
        this.id = id;
        this.keywords = keywords == null ? "" : keywords;
        this.replyContent = replyContent == null ? "" : replyContent;
        this.hitCount = hitCount;
        this.status = status == null ? FaqStatus.ENABLED : status;
        this.updatedAtMillis = updatedAtMillis;
    }

    /** 拆好的关键词列表：去空白、去空串，空条目直接跳过（脏数据不该产生空词把一切都匹配上）。 */
    public List<String> keywordList() {
        List<String> list = new ArrayList<>();
        for (String raw : keywords.split("[,，]")) {
            String word = raw.trim();
            if (!word.isEmpty()) {
                list.add(word);
            }
        }
        return list;
    }

    /** 是否参与匹配：只有启用条目才该进机器人的候选集。 */
    public boolean isEnabled() {
        return status == FaqStatus.ENABLED;
    }

    public long getId() {
        return id;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getReplyContent() {
        return replyContent;
    }

    public int getHitCount() {
        return hitCount;
    }

    public FaqStatus getStatus() {
        return status;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }
}
