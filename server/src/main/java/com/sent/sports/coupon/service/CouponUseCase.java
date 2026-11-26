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
 * <p>「能不能用、能抵多少」全部交给 {@link Coupon#applicability}（R9 唯一真源），
 * 本类只负责三件工程事：查 PO 转领域对象、按需补 SPU 映射、把行金额规整成领域行。</p>
 */
@Service
public class CouponUseCase implements CouponDeduction {

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
        if (couponId <= 0L || lines == null || lines.isEmpty()) {
            return 0L;
        }
        CustomerCouponPO po = couponMapper.selectByIdAndCustomer(couponId, customerId);
        if (po == null) {
            return 0L;
        }
        Coupon coupon = CouponAssembler.toDomain(po);
        // SPU 映射只在品类券按 SPU 限定时才查：多打一次商品库查询换不来任何信息
        Map<Long, Long> spuIds = Coupon.SCOPE_SPU.equals(coupon.scopeType())
                ? productQueryService.spuIdsOfSkus(lines.stream().map(CouponLine::skuId).toList())
                : Map.of();
        List<Coupon.Line> rows = lines.stream()
                .map(line -> new Coupon.Line(line.skuId(), spuIds.get(line.skuId()), line.amountCent()))
                .toList();
        long goodsCent = lines.stream().mapToLong(line -> Math.max(0L, line.amountCent())).sum();
        return coupon.applicability(customerQueryService.typeOf(customerId), goodsCent, rows,
                LocalDateTime.now()).discountCent();
    }

    /** SQL 带 status='UNUSED' 条件，重复核销影响行数为 0（按幂等成功处理）。 */
    @Override
    public void markUsed(long couponId, String orderNo) {
        if (couponId > 0) couponMapper.markUsed(couponId, orderNo);
    }

    /** 整单退：把该订单核销的券退回未使用（SQL 带 status='USED'，重复退影响 0 行）。 */
    @Override
    public int returnCoupon(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return 0;
        }
        return couponMapper.returnByOrderNo(orderNo);
    }
}
