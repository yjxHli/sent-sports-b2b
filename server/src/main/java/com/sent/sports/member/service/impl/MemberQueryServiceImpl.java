package com.sent.sports.member.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.member.domain.MemberTier;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.mapper.MemberOverviewMapper;
import com.sent.sports.member.mapper.po.MemberSnapshotPO;
import com.sent.sports.member.service.MemberQueryService;
import com.sent.sports.product.service.MemberExclusiveService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * 会员查询实现（收口阶段）：从「恒抛 40401 的桩」换成真读 {@code customer} /
 * {@code enterprise_profile} / {@code receivable}。
 * <p>此前三个读方法直接 {@code throw NOT_FOUND}，管理端「会员与积分」顶部三张卡
 * 永远显示 {@code --}，运营也无从确认当前看的是哪个账号。现在：
 * <ul>
 *   <li>等级：读 {@code customer.level_code} + {@code growth_value} + {@code level_valid_to}，
 *       按 {@link MemberTier} 算出<b>有效等级</b>——等级码滞后于成长值时以成长值为准
 *       （成长值 16167 必须是 LV2，而不是 LV1 + 下一档 5000 + 差额 -11167），
 *       人工授级到期后回落到成长值档位；</li>
 *   <li>积分：读 {@code customer.points_balance}；</li>
 *   <li>额度：读 {@code enterprise_profile} 的总额度与已用额度，可用 = 总额 − 已用，
 *       同时回传未结应收。</li>
 * </ul>
 * 客户不存在仍抛 40401（不静默返回空卡片），让前端能明确提示「该客户不存在」。</p>
 */
@Service
public class MemberQueryServiceImpl implements MemberQueryService {

    private final MemberOverviewMapper memberOverviewMapper;
    private final MemberExclusiveService memberExclusiveService;
    private final Clock clock;

    public MemberQueryServiceImpl(MemberOverviewMapper memberOverviewMapper,
                                  MemberExclusiveService memberExclusiveService,
                                  Clock clock) {
        this.memberOverviewMapper = memberOverviewMapper;
        this.memberExclusiveService = memberExclusiveService;
        this.clock = clock;
    }

    @Override
    public MemberDtos.LevelVO levelProgress(Long customerId) {
        MemberSnapshotPO po = requireSnapshot(customerId);
        long growth = po.getGrowthValue() == null ? 0L : po.getGrowthValue();
        long validToMillis = TimeUtil.toMillis(po.getLevelValidTo());
        long now = clock.instant().toEpochMilli();
        boolean grantActive = validToMillis > 0L && now <= validToMillis;
        MemberTier effective = effectiveTier(po);

        long current = effective.getThreshold();
        MemberTier nextTier = effective.next();
        long next = nextTier == null ? current : nextTier.getThreshold();
        double progress = nextTier == null || next <= current
                ? 1.0d
                : clamp((growth - current) / (double) (next - current));
        return new MemberDtos.LevelVO(po.getCustomerId(), effective.getCode(), growth, current, next,
                Math.round(progress * 10000d) / 10000d, grantActive ? validToMillis : 0L);
    }

    @Override
    public MemberDtos.PointVO pointDetail(Long customerId) {
        MemberSnapshotPO po = requireSnapshot(customerId);
        long points = po.getPointsBalance() == null ? 0L : po.getPointsBalance();
        return new MemberDtos.PointVO(po.getCustomerId(), points);
    }

    @Override
    public MemberDtos.CreditVO credit(Long customerId) {
        MemberSnapshotPO po = requireSnapshot(customerId);
        long limit = po.getCreditLimitCent() == null ? 0L : po.getCreditLimitCent();
        long used = po.getCreditUsedCent() == null ? 0L : po.getCreditUsedCent();
        Long receivable = memberOverviewMapper.sumOpenReceivable(po.getCustomerId());
        long receivableCent = receivable == null ? 0L : receivable;
        return new MemberDtos.CreditVO(po.getCustomerId(), limit, MoneyUtil.toYuan(limit),
                receivableCent, MoneyUtil.toYuan(receivableCent),
                used, Math.max(limit - used, 0L));
    }

    @Override
    public List<MemberDtos.ExclusiveProductVO> exclusiveProducts(Long customerId) {
        MemberSnapshotPO po = requireSnapshot(customerId);
        MemberTier effective = effectiveTier(po);
        return memberExclusiveService.listFor(po.getCustomerId(), effective.ordinal()).stream()
                .map(p -> new MemberDtos.ExclusiveProductVO(
                        String.valueOf(p.productId()),
                        p.productName(),
                        p.priceCent(),
                        MoneyUtil.toYuan(p.priceCent()),
                        p.mainImage(),
                        p.source()))
                .toList();
    }

    /**
     * 有效等级（与 {@link #levelProgress} 同口径）：授级到期按成长值重算，未到期取「授级 / 成长值」较高者。
     * 抽出来是为了让「等级进度」与「专属商品门槛」用同一份判断——两处各写一遍必然漂移。
     */
    private MemberTier effectiveTier(MemberSnapshotPO po) {
        long growth = po.getGrowthValue() == null ? 0L : po.getGrowthValue();
        MemberTier stored = MemberTier.of(po.getLevelCode());
        MemberTier fromGrowth = MemberTier.calc(growth);
        long validToMillis = TimeUtil.toMillis(po.getLevelValidTo());
        boolean expiredGrant = validToMillis > 0L && clock.instant().toEpochMilli() > validToMillis;
        return expiredGrant ? fromGrowth : MemberTier.max(stored, fromGrowth);
    }

    /** 客户不存在或已软删：明确 40401，前端才能提示「该客户不存在」而不是显示空卡片。 */
    private MemberSnapshotPO requireSnapshot(Long customerId) {
        long id = customerId == null ? 0L : customerId;
        if (id <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户ID不合法");
        }
        MemberSnapshotPO po = memberOverviewMapper.selectSnapshot(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "客户不存在：" + id);
        }
        return po;
    }

    private static double clamp(double value) {
        if (value < 0d) {
            return 0d;
        }
        return Math.min(value, 1.0d);
    }
}
