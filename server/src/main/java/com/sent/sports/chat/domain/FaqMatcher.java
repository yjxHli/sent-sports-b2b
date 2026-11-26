package com.sent.sports.chat.domain;

import java.util.List;
import java.util.Optional;

/**
 * 知识库匹配策略（2026-11-24）：轻量关键词权重匹配，纯函数、无依赖，便于单测。
 *
 * <p>为什么不上分词：知识库是运营手填的十来个条目，客户问法集中在固定几个词（发货/物流/定制…），
 * 上分词器要引词表与词典维护成本，收益不成正比。这里用「命中关键词个数 → 最长命中词长」两级排序：
 * 命中越多越相关；同样多时更长的词更具体（「印号」比「号」更准）。</p>
 *
 * <p>并列时保留先出现的条目（按 id 升序传入），保证同一句话永远命中同一条，便于回归断言。</p>
 */
public final class FaqMatcher {

    private FaqMatcher() {
    }

    /**
     * 在候选条目里找最匹配的一条。
     *
     * @param text       客户原话（可为 null / 空白）
     * @param candidates 知识库条目（建议按 id 升序）
     * @return 命中的条目；没有任何关键词出现在文本里时返回 empty
     */
    public static Optional<FaqKnowledge> bestMatch(String text, List<FaqKnowledge> candidates) {
        if (text == null || text.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String haystack = text.toLowerCase();
        FaqKnowledge best = null;
        int bestScore = 0;
        int bestLength = 0;
        for (FaqKnowledge candidate : candidates) {
            int hits = 0;
            int longest = 0;
            for (String keyword : candidate.keywordList()) {
                String needle = keyword.toLowerCase();
                if (haystack.contains(needle)) {
                    hits++;
                    longest = Math.max(longest, needle.length());
                }
            }
            if (hits == 0) {
                continue;
            }
            int score = hits * 100 + longest;
            // 严格大于：同分保留先出现的条目，结果稳定
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                bestLength = longest;
            }
        }
        return best == null || bestLength == 0 ? Optional.empty() : Optional.of(best);
    }
}
