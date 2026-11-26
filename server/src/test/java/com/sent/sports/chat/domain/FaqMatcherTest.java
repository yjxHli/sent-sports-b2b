package com.sent.sports.chat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库匹配策略测试（2026-11-24，TDD 红）。
 *
 * <p>口径：命中关键词越多越相关；同样多时更长的关键词更具体；并列保留先出现的条目。</p>
 */
class FaqMatcherTest {

    private static FaqKnowledge faq(long id, String keywords, String reply) {
        return new FaqKnowledge(id, keywords, reply, 0);
    }

    @Test
    @DisplayName("命中任一关键词即匹配，回复取该条目原文")
    void singleKeywordHits() {
        List<FaqKnowledge> dict = List.of(
                faq(1L, "发货,物流,快递", "亲，我们默认发顺丰，48小时内发货哦~"),
                faq(2L, "定制,图稿", "定制商品请先在订单详情上传图稿~"));

        Optional<FaqKnowledge> hit = FaqMatcher.bestMatch("你们发什么物流呀", dict);

        assertTrue(hit.isPresent());
        assertEquals(1L, hit.get().getId());
        assertEquals("亲，我们默认发顺丰，48小时内发货哦~", hit.get().getReplyContent());
    }

    @Test
    @DisplayName("命中关键词更多的条目优先（哪怕它排后面）")
    void moreHitsWin() {
        List<FaqKnowledge> dict = List.of(
                faq(1L, "定制", "泛泛的定制答复"),
                faq(2L, "定制,图稿,印号", "定制商品请先在订单详情上传图稿~"));

        Optional<FaqKnowledge> hit = FaqMatcher.bestMatch("定制球衣的印号图稿在哪传", dict);

        assertTrue(hit.isPresent());
        assertEquals(2L, hit.get().getId());
    }

    @Test
    @DisplayName("命中数相同时更长的关键词更具体，优先命中")
    void longerKeywordWins() {
        List<FaqKnowledge> dict = List.of(
                faq(1L, "号", "关于号的答复"),
                faq(2L, "印号", "印号答复"));

        Optional<FaqKnowledge> hit = FaqMatcher.bestMatch("球衣印号怎么弄", dict);

        assertTrue(hit.isPresent());
        assertEquals(2L, hit.get().getId());
    }

    @Test
    @DisplayName("完全没命中返回空，让上层走转人工而不是硬答")
    void noKeywordReturnsEmpty() {
        List<FaqKnowledge> dict = List.of(faq(1L, "发货,物流", "发货答复"));

        assertTrue(FaqMatcher.bestMatch("你们老板姓什么", dict).isEmpty());
    }

    @Test
    @DisplayName("空文本 / 空词库 / 空关键词段都不匹配，且不抛异常")
    void blankInputsAreSafe() {
        List<FaqKnowledge> dict = List.of(faq(1L, " , ,", "不该被命中"));

        assertTrue(FaqMatcher.bestMatch(null, dict).isEmpty());
        assertTrue(FaqMatcher.bestMatch("   ", dict).isEmpty());
        assertTrue(FaqMatcher.bestMatch("发货", List.of()).isEmpty());
        assertTrue(FaqMatcher.bestMatch("发货", dict).isEmpty(), "全是空关键词的条目不能把一切都匹配上");
    }

    @Test
    @DisplayName("大小写不敏感：英文关键词用大写问也要命中")
    void matchingIsCaseInsensitive() {
        List<FaqKnowledge> dict = List.of(faq(1L, "iphone,手机", "手机答复"));

        assertTrue(FaqMatcher.bestMatch("IPHONE 能开发票吗", dict).isPresent());
    }
}
