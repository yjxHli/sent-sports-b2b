package com.sent.sports.coupon.service;

import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.dto.CouponLine;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.product.service.ProductQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 下单券抵扣与核销。取价预览和真实下单都调用 {@link #discountFor} 得同一口径，
 * 避免「预览便宜、实付贵」。券无效时返回 0 而不抛错：前端已按可用券列表过滤，
 * 这里只是服务端兜底防篡改。
 *
 * <p>四类券型的差异只体现在「哪些行金额计入门槛分母」：</p>
 * <ul>
 *   <li>无门槛 / 满减：全部行金额计入；</li>
 *   <li>品类券：只有命中 SPU/SKU 的行金额计入；</li>
 *   <li>企业专属券：非企业客户直接 0，企业客户按全部行金额计入。</li>
 * </ul>
 */
@Service
public class CouponUseCase implements CouponDeduction {

    private static final String CUSTOMER_ENTERPRISE = "ENTERPRISE";

    private final CouponMapper couponMapper;
    private final CustomerQueryService customerQueryService;
    private final ProductQueryService productQueryService;

    public CouponUseCase(CouponMapper couponMapper,
                         CustomerQueryService customerQueryService,
                         ProductQueryService productQueryService) {
        this.couponMapper = couponMapper;
        this.customerQueryService = customerQueryService;
        this.productQueryService = productQueryService;
    }

    @Override
    public long discountFor(long couponId, long customerId, List<CouponLine> lines) {
        if (couponId <= 0L || lines == null || lines.isEmpty()) return 0L;
        CustomerCouponPO po = couponMapper.selectByIdAndCustomer(couponId, customerId);
        if (po == null) return 0L;
        Coupon coupon = CouponAssembler.toDomain(po);
        if (coupon.isEnterpriseOnly()
                && !CUSTOMER_ENTERPRISE.equals(customerQueryService.typeOf(customerId))) {
            return 0L;
        }
        Map<Long, Long> spuIds = Coupon.SCOPE_SPU.equals(coupon.scopeType())
                ? productQueryService.spuIdsOfSkus(lines.stream().map(CouponLine::skuId).toList())
                : Map.of();
        long eligible = 0L;
        for (CouponLine line : lines) {
            if (coupon.matchesSku(line.skuId(), spuIds.get(line.skuId()))) {
                eligible += Math.max(0L, line.amountCent());
            }
        }
        return coupon.usableFor(eligible, LocalDateTime.now()) ? coupon.discountFor(eligible) : 0L;
    }

    /** SQL 带 status='UNUSED' 条件，重复核销影响行数为 0（按幂等成功处理）。 */
    @Override
    public void markUsed(long couponId, String orderNo) {
        if (couponId > 0) couponMapper.markUsed(couponId, orderNo);
    }
}
