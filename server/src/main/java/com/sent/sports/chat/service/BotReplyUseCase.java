package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.FaqKnowledge;
import com.sent.sports.chat.domain.FaqMatcher;
import com.sent.sports.chat.repository.FaqKnowledgeRepository;
import com.sent.sports.chat.service.dto.BotReplyOutcome;
import com.sent.sports.common.anno.UseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 机器人应答决策（2026-11-24）：只回答「这句话机器人管不管、怎么管」，不落库、不推送。
 *
 * <p>四档口径：
 * <ol>
 *   <li>非 BOT 会话 → {@link BotReplyOutcome.BotAction#NONE}，调用方走原链路；</li>
 *   <li>含「转人工 / 人工 / 真人」等指令 → TRANSFER，切人工并提示「正在为您转接…」；</li>
 *   <li>知识库命中 → REPLY，直接回原文，不打扰坐席；</li>
 *   <li>都没命中 → HANDOFF，静默切人工进排队（机器人不硬撑，也不假装懂）。</li>
 * </ol>
 *
 * <p>为什么「答不上来」默认转人工而不是回一句「我不知道」：客服场景里客户是来解决问题的，
 * 机器人反复道歉只会让人更烦；直接进人工队列的等待时间与原来的进线体验一致。</p>
 */
@Service
public class BotReplyUseCase {

    /** 转人工提示语：前端把 SYSTEM 帧渲染成居中灰底提示条。 */
    public static final String TRANSFER_TIP = "正在为您转接人工客服，请稍候...";

    /** 客户表达「要人」的常见说法：命中任意一个即转人工。 */
    private static final List<String> TRANSFER_WORDS = List.of("转人工", "人工客服", "人工", "真人", "找客服");

    private final FaqKnowledgeRepository faqKnowledgeRepository;

    public BotReplyUseCase(FaqKnowledgeRepository faqKnowledgeRepository) {
        this.faqKnowledgeRepository = faqKnowledgeRepository;
    }

    @UseCase
    public BotReplyOutcome decide(ChatSession session, String content) {
        if (session == null || !session.isBot()) {
            return BotReplyOutcome.none();
        }
        if (isTransferCommand(content)) {
            return new BotReplyOutcome(BotReplyOutcome.BotAction.TRANSFER, TRANSFER_TIP);
        }
        // 空白消息（WS 上行兜底可能塞进来）不查库：查了也必然不命中，还白跑一次全表
        if (content == null || content.isBlank()) {
            return new BotReplyOutcome(BotReplyOutcome.BotAction.HANDOFF, null);
        }
        Optional<FaqKnowledge> hit = FaqMatcher.bestMatch(content, faqKnowledgeRepository.findAllEnabledOrdered());
        if (hit.isPresent()) {
            // 命中计数只做运营统计，写在最前面也无所谓失败：仓储实现内部吞掉异常
            faqKnowledgeRepository.recordHit(hit.get().getId());
            return new BotReplyOutcome(BotReplyOutcome.BotAction.REPLY, hit.get().getReplyContent());
        }
        return new BotReplyOutcome(BotReplyOutcome.BotAction.HANDOFF, null);
    }

    /**
     * 是否是「要人工」的指令。
     * <p>用包含匹配而不是全等：客户常说「我要转人工」「帮我找人工客服」，
     * 全等匹配等于逼客户背口令。代价是「人工草坪」这类词会误判——
     * 运动用品场景里几乎不会出现，真出现也只会多转一次人工，代价可接受。</p>
     */
    public boolean isTransferCommand(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String text = content.trim();
        return TRANSFER_WORDS.stream().anyMatch(text::contains);
    }
}
