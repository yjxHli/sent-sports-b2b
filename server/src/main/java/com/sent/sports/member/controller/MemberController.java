package com.sent.sports.member.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.service.AdjustPointUseCase;
import com.sent.sports.member.service.GrantLevelUseCase;
import com.sent.sports.member.service.MemberQueryService;
import com.sent.sports.member.service.dto.AdjustPointCmd;
import com.sent.sports.member.service.dto.AdjustPointResult;
import com.sent.sports.member.service.dto.GrantLevelCmd;
import com.sent.sports.member.service.dto.GrantLevelResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员控制器（P4-28）：等级进度 / 积分明细 / 额度应收 / 专属商品（读面）+ 授级 / 积分调整（写面，plan §8.8）。
 * <p>读面依赖同模块查询门面 {@link MemberQueryService}（桩，P5 接入仓储）；
 * 写面依赖同模块用例 {@link GrantLevelUseCase} / {@link AdjustPointUseCase}（符合 ArchUnit 规则 4）。
 * 当前用户绑定（@CurrentUserArg）在 Phase 5，读面以占位客户号查询；写面操作人（operatorId）以占位常量，真实绑定 P5。</p>
 */
@RestController
@RequestMapping("/api/member")
public class MemberController {

    /** 占位常量：Phase 5 由 @CurrentUserArg 注入真实当前用户 / 操作人。 */
    private static final long CURRENT_USER_PLACEHOLDER = 0L;

    private final MemberQueryService memberQueryService;
    private final GrantLevelUseCase grantLevelUseCase;
    private final AdjustPointUseCase adjustPointUseCase;

    public MemberController(MemberQueryService memberQueryService, GrantLevelUseCase grantLevelUseCase,
                            AdjustPointUseCase adjustPointUseCase) {
        this.memberQueryService = memberQueryService;
        this.grantLevelUseCase = grantLevelUseCase;
        this.adjustPointUseCase = adjustPointUseCase;
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
     *   <li>显式 {@code customerId}（管理端「会员与积分」按客户查看）优先；</li>
     *   <li>否则取当前登录者（用户端 aud=user 时 sub 即客户号）；</li>
     *   <li>都没有时退回占位 0，由查询服务回 40001，不会误读别人的数据。</li>
     * </ol>
     */
    private static long resolveCustomerId(CurrentUser user, Long customerId) {
        if (customerId != null && customerId > 0L) {
            return customerId;
        }
        if (user != null && CurrentUser.AUD_USER.equals(user.aud())) {
            return user.userId();
        }
        return CURRENT_USER_PLACEHOLDER;
    }

    @PostMapping("/grant-level")
    @RequirePerm("member:grant")
    public R<MemberDtos.GrantResultVO> grantLevel(@Valid @RequestBody MemberDtos.GrantLevelCmd cmd) {
        GrantLevelResult r = grantLevelUseCase.grant(new GrantLevelCmd(
                cmd.customerId(), cmd.targetLevel(), cmd.validToMillis(), cmd.reason(), CURRENT_USER_PLACEHOLDER));
        return R.ok(new MemberDtos.GrantResultVO(r.customerId(), r.level().name(), r.validToMillis()));
    }

    @PostMapping("/points/adjust")
    @RequirePerm("member:adjust")
    public R<MemberDtos.AdjustResultVO> adjustPoints(@Valid @RequestBody MemberDtos.AdjustPointCmd cmd) {
        AdjustPointResult r = adjustPointUseCase.adjust(new AdjustPointCmd(
                cmd.customerId(), cmd.delta(), cmd.reason(), cmd.bizType(), cmd.bizNo(), CURRENT_USER_PLACEHOLDER));
        return R.ok(new MemberDtos.AdjustResultVO(r.customerId(), r.beforePoints(), r.afterPoints(), r.delta()));
    }
}
