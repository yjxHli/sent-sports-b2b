package com.sent.sports.chat.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 机器人知识库 {@code faq_knowledge} 持久化对象（V59）。
 * <p>数据量小（运营手填十几条），读取走全量查询后在内存里做关键词匹配，
 * 不在 SQL 里拼 LIKE——多关键词组合的权重排序用 SQL 表达既难读也难维护。</p>
 */
@TableName("faq_knowledge")
public class FaqKnowledgePO extends BasePO {

    /** 关键词，英文逗号分隔。 */
    private String keywords;

    /** 命中后的回复原文。 */
    private String replyContent;

    /** 命中次数（运营据此优化知识库，不参与匹配）。 */
    private Integer hitCount;

    /** 启停状态（V60）：ENABLED 参与匹配 / DISABLED 保留但不回复。 */
    private String status;

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getReplyContent() {
        return replyContent;
    }

    public void setReplyContent(String replyContent) {
        this.replyContent = replyContent;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
