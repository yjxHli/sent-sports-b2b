package com.sent.sports.customize.service.impl;

import com.sent.sports.customize.domain.CustomFeeCalculator;
import com.sent.sports.customize.domain.CustomFeeConfig;
import com.sent.sports.customize.domain.CustomFeeResult;
import com.sent.sports.customize.domain.FaceSide;
import com.sent.sports.customize.service.CustomFeeFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 定制计费门面实现：装配一次 {@link CustomFeeConfig}，其余全部委托
 * 领域 {@link CustomFeeCalculator}（面数单价 × 数量阶梯折扣）。
 *
 * <p>为什么配置在 yml 而不在代码里：审计 R6 的验收口径是「调价只改 yml」。
 * 当前订单行只带 {@code customFlag}（还没有面数字段），因此一律按单面计价；
 * 接入 {@code faceSide} 时只需在这里按行传入对应面数，两个调用方零改动。</p>
 */
@Service
public class CustomFeeFacadeImpl implements CustomFeeFacade {

    /** 单面（UV 单面印刷）基础单价，默认 1000 分 = 10 元/件（2026-11-17 用户口径）。 */
    private static final long DEFAULT_SINGLE_CENT = 1000L;

    private final CustomFeeConfig config;

    public CustomFeeFacadeImpl(@Value("${app.custom-fee.single-cent:1000}") long singleCent) {
        long base = singleCent > 0L ? singleCent : DEFAULT_SINGLE_CENT;
        // 阶梯折扣暂不启用（保持 10 元/件的历史口径）：引擎已支持，放开只需在这里加 QtyTier
        this.config = new CustomFeeConfig(Map.of(FaceSide.SINGLE, base), List.of());
    }

    @Override
    public long feeCent(int qty) {
        if (qty <= 0) {
            return 0L;
        }
        CustomFeeResult result = CustomFeeCalculator.calc(config, FaceSide.SINGLE, qty);
        return result.getTotalFeeCent();
    }
}
