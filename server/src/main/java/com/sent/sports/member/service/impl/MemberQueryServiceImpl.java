package com.sent.sports.member.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.mapper.MemberOverviewMapper;
import com.sent.sports.member.mapper.po.MemberSnapshotPO;
import com.sent.sports.member.service.MemberQueryService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 会员查询实现（收口阶段）：从「恒抛 40401 的桩」换成真读 {@code customer} /
 * {@code enterprise_profile} / {@code receivable}。
 * <p>此前三个读方法直接 {@code throw NOT_FOUND}，管理端「会员与积分」顶部三张卡
 * 永远显示 {@code --}，运营也无从确认当前看的是哪个账号。现在：
 * <ul>
 *   <li>等级：读 {@code customer.level_code} + {@code growth_value}，按 NORMAL/LV1/LV2/LV3
 *       门槛算出下一档与进度；</li>
 *   <li>积分：读 {@code customer.points_balance}；</li>
 *   <li>额度：读 {@code enterprise_profile} 的总额度与已用额度，可用 = 总额 − 已用，
 *       同时回传未结应收。</li>
 * </ul>
 * 客户不存在仍抛 40401（不静默返回空卡片），让前端能明确提示「该客户不存在」。</p>
 */
@Service
public class MemberQueryServiceImpl implements MemberQueryService {

    /** 等级码升序：与真库 {@code customer.level_code} 值域一致。 */
    private static final List<String> LEVELS = List.of("NORMAL", "LV1", "LV2", "LV3");

    /** 各等级成长值门槛，下标与 {@link #LEVELS} 对齐；仅用于展示进度，取价仍以后端引擎为准。 */
    private static final Map<String, Long> THRESHOLDS = Map.of(
            "NORMAL", 0L,
            "LV1", 1000L,
            "LV2", 5000L,
            "LV3", 20000L);

    private final MemberOverviewMapper memberOverviewMapper;

    public MemberQueryServiceImpl(MemberOverviewMapper memberOverviewMapper) {
        this.memberOverviewMapper = memberOverviewMapper;
    }

    @Override
    public MemberDtos.LevelVO levelProgress(Long customerId) {
        MemberSnapshotPO po = requireSnapshot(customerId);
        String level = normalizeLevel(po.getLevelCode());
        long growth = po.getGrowthValue() == null ? 0L : po.getGrowthValue();
        long current = THRESHOLDS.get(level);
        int index = LEVELS.indexOf(level);
        boolean hasNext = index >= 0 && index + 1 < LEVELS.size();
        long next = hasNext ? THRESHOLDS.get(LEVELS.get(index + 1)) : current;
        double progress = hasNext && next > current
                ? clamp((growth - current) / (double) (next - current))
                : 1.0d;
        return new MemberDtos.LevelVO(po.getCustomerId(), level, growth, current, next,
                Math.round(progress * 10000d) / 10000d, 0L);
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
        // 专属商品取价依赖一客一价引擎，属于另一条读路径；当前没有配置时返回空列表。
        return Collections.emptyList();
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

    private static String normalizeLevel(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) {
            return "NORMAL";
        }
        String upper = levelCode.trim().toUpperCase();
        return THRESHOLDS.containsKey(upper) ? upper : "NORMAL";
    }

    private static double clamp(double value) {
        if (value < 0d) {
            return 0d;
        }
        return Math.min(value, 1.0d);
    }
}
