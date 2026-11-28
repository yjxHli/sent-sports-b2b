package com.sent.sports.member.controller;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.service.MemberQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员控制器（P4-28，2026-11-28 收缩为只读面）：等级进度 / 积分明细 / 额度应收 / 专属商品。
 *
 * <p>原先还有 {@code POST /grant-level} 与 {@code POST /points/adjust} 两个写端点，
 * 但它们背后挂的是进程内内存仓储（{@code InMemoryMemberRepository} / {@code InMemoryPointFlowRepository}），
 * 调用必然查无客户；管理端写操作早在 P5 已迁到
 * {@code PUT /api/admin/customers/{id}/level} 与 {@code POST /api/admin/customers/{id}/points}
 * （落真库 {@code customer} / {@code point_log}）。这两个死端点已于 2026-11-28 下线，
 * 本控制器现在只依赖同模块查询门面 {@link MemberQueryService}。</p>
 */
@RestController
@RequestMapping("/api/member")
public class MemberController {

    /** 占位常量：Phase 5 由 @CurrentUserArg 注入真实当前用户 / 操作人。 */
    private static final long CURRENT_USER_PLACEHOLDER = 0L;

    private final MemberQueryService memberQueryService;

    public MemberController(MemberQueryService memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    @GetMapping("/level")
    public R<MemberDtos.LevelVO> level(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestParam(required = false) Long customerId) {
        return R.ok(memberQueryService.levelProgress(resolveCustomerId(user, customerId)));
    }

    @GetMapping("/points")
    public R<MemberDtos.PointVO> points(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestParam(required = false) Long customerId) {
        return R.ok(memberQueryService.pointDetail(resolveCustomerId(user, customerId)));
    }

    @GetMapping("/credit")
    public R<MemberDtos.CreditVO> credit(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestParam(required = false) Long customerId) {
        return R.ok(memberQueryService.credit(resolveCustomerId(user, customerId)));
    }

    @GetMapping("/exclusive-products")
    public R<List<MemberDtos.ExclusiveProductVO>> exclusiveProducts(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestParam(required = false) Long customerId) {
        return R.ok(memberQueryService.exclusiveProducts(resolveCustomerId(user, customerId)));
    }

    /**
     * 概览作用于哪个客户：
     * <ol>
     *   <li>管理端令牌 + 显式 {@code customerId}（管理端「会员与积分」按客户查看）→ 用该客户；</li>
     *   <li>否则取当前登录者（用户端 aud=user 时 sub 即客户号）；</li>
     *   <li>都没有时退回占位 0，由查询服务回 40001，不会误读别人的数据。</li>
     * </ol>
     *
     * <p><b>2026-11-28 批次 1 / P0-03</b>：此前「显式 customerId 优先」，客户只要手改 query 参数
     * 就能读到他人的积分 / 授信 / 应收。现在显式参数只在管理员令牌下生效，普通客户一律以自己的
     * 客户号为准——「只能查询自己的 ID」由服务端强制，不依赖前端自觉。</p>
     */
    private static long resolveCustomerId(CurrentUser user, Long customerId) {
        if (user != null && user.isAdmin() && customerId != null && customerId > 0L) {
            return customerId;
        }
        if (user != null && CurrentUser.AUD_USER.equals(user.aud())) {
            return user.userId();
        }
        return CURRENT_USER_PLACEHOLDER;
    }
}
