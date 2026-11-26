package com.sent.sports.integration;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SessionType;
import com.sent.sports.chat.dto.admin.AdminFaqDtos;
import com.sent.sports.chat.service.BotReplyUseCase;
import com.sent.sports.chat.service.FaqAdminService;
import com.sent.sports.chat.service.dto.BotReplyOutcome;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.support.FullStackIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 话术配置热更新集成测试（V60，TDD 红）。
 *
 * <p>这是整个需求的验收核心：<b>管理端把话术写进库之后，客户端的模糊查询必须立刻命中新内容</b>，
 * 不允许「重启后端才生效」「等缓存过期才生效」。所以断言不看接口返回码，只看
 * {@link BotReplyUseCase#decide} 这个真实匹配入口——它就是用户端发消息时走的那段逻辑。</p>
 *
 * <p>三条链路一起覆盖：新增秒生效 → 修改秒生效 → 停用秒失效。
 * 如果写路径漏调缓存失效，第二条断言会拿回旧话术，这里立刻红。</p>
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class FaqHotReloadIT extends FullStackIntegrationTest {

    private static final long OPERATOR = 9001L;
    private static final String KEYWORDS = "退换货";
    private static final String REPLY = "支持7天无理由";

    @Autowired
    private FaqAdminService faqAdminService;

    @Autowired
    private BotReplyUseCase botReplyUseCase;

    /** 机器人接待中的会话：只有 BOT 会话才会走知识库匹配。 */
    private ChatSession botSession() {
        return new ChatSession(7001L, 9002L, false, ChatSessionStatus.WAITING, null, 0L, 0L, 0, SessionType.BOT);
    }

    private String replyTo(String customerWords) {
        BotReplyOutcome outcome = botReplyUseCase.decide(botSession(), customerWords);
        assertThat(outcome.action()).as("客户说「%s」", customerWords)
                .isEqualTo(BotReplyOutcome.BotAction.REPLY);
        return outcome.reply();
    }

    @Test
    @DisplayName("管理端新增话术后，用户端匹配立刻命中（无需重启 / 无缓存延迟）")
    void createTakesEffectImmediately() {
        // 前置：知识库为空（IT 每次用例前清表），此时机器人只会转人工
        assertThat(botReplyUseCase.decide(botSession(), "我要退换货").action())
                .isEqualTo(BotReplyOutcome.BotAction.HANDOFF);

        AdminFaqDtos.FaqVO created = faqAdminService.create(
                new AdminFaqDtos.FaqCmd(KEYWORDS, REPLY, "ENABLED"), OPERATOR);

        assertThat(created.id()).isPositive();
        assertThat(created.hitCount()).isZero();
        assertThat(replyTo("我要退换货")).isEqualTo(REPLY);

        // 留痕：新增要能在操作日志里查到（module=FAQ / action=CREATE）
        Integer logs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE module = 'FAQ' AND action = 'CREATE' "
                        + "AND target_type = 'FAQ_KNOWLEDGE'", Integer.class);
        assertThat(logs).as("新增话术必须写操作日志").isEqualTo(1);
    }

    @Test
    @DisplayName("管理端修改话术后，同一句话立刻拿到新回复（缓存必须被写路径失效）")
    void updateTakesEffectImmediately() {
        AdminFaqDtos.FaqVO created = faqAdminService.create(
                new AdminFaqDtos.FaqCmd(KEYWORDS, "旧话术：支持7天退换", "ENABLED"), OPERATOR);
        assertThat(replyTo("我要退换货")).isEqualTo("旧话术：支持7天退换");

        faqAdminService.update(created.id(),
                new AdminFaqDtos.FaqCmd(KEYWORDS, REPLY, "ENABLED"), OPERATOR);

        assertThat(replyTo("我要退换货"))
                .as("改完话术下一条消息就要用新的，不能等缓存过期")
                .isEqualTo(REPLY);
    }

    @Test
    @DisplayName("管理端停用话术后，同一句话立刻不再命中（回到人工）")
    void disableStopsMatchingImmediately() {
        AdminFaqDtos.FaqVO created = faqAdminService.create(
                new AdminFaqDtos.FaqCmd(KEYWORDS, REPLY, "ENABLED"), OPERATOR);
        assertThat(replyTo("我要退换货")).isEqualTo(REPLY);

        faqAdminService.updateStatus(created.id(), "DISABLED", OPERATOR);

        assertThat(botReplyUseCase.decide(botSession(), "我要退换货").action())
                .as("停用只是暂时不答，话术仍保留在库里")
                .isEqualTo(BotReplyOutcome.BotAction.HANDOFF);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM faq_knowledge WHERE id = ?", String.class, created.id()))
                .isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("管理端列表能看到命中次数：运营据此判断话术有没有用")
    void listReportsHitCount() {
        AdminFaqDtos.FaqVO created = faqAdminService.create(
                new AdminFaqDtos.FaqCmd("发货,物流", "默认发顺丰", "ENABLED"), OPERATOR);
        replyTo("什么时候发货");
        replyTo("物流到哪了");

        PageResult<AdminFaqDtos.FaqVO> page = faqAdminService.list("物流", "ENABLED", new PageQuery());
        assertThat(page.getTotal()).isEqualTo(1);
        AdminFaqDtos.FaqVO row = page.getList().get(0);
        assertThat(row.id()).isEqualTo(created.id());
        assertThat(row.hitCount()).as("两次命中都要计数").isEqualTo(2);
        assertThat(row.updatedAtMillis()).isPositive();
    }
}
