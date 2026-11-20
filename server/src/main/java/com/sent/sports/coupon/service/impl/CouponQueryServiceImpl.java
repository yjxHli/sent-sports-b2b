package com.sent.sports.coupon.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.CouponAssembler;
import com.sent.sports.coupon.service.CouponQueryService;
import com.sent.sports.coupon.service.MemberDayPolicy;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.product.service.ProductQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CouponQueryServiceImpl implements CouponQueryService {

    private final CouponMapper couponMapper;
    private final CouponTemplateMapper templateMapper;
    private final CustomerQueryService customerQueryService;
    private final ProductQueryService productQueryService;

    public CouponQueryServiceImpl(CouponMapper couponMapper, CouponTemplateMapper templateMapper,
                                  CustomerQueryService customerQueryService,
                                  ProductQueryService productQueryService) {
        this.couponMapper = couponMapper;
        this.templateMapper = templateMapper;
        this.customerQueryService = customerQueryService;
        this.productQueryService = productQueryService;
    }

    @Override
    public List<CouponDtos.CouponVO> listMine(long customerId) {
        LocalDateTime now = LocalDateTime.now();
        return couponMapper.selectByCustomer(customerId).stream()
                .map(po -> toVO(po, now))
                .toList();
    }

    @Override
    public List<CouponDtos.CouponVO> listUsable(long customerId, long goodsCent) {
        return listUsable(customerId, goodsCent, List.of());
    }

    @Override
    public List<CouponDtos.CouponVO> listUsable(long customerId, long goodsCent, List<Long> skuIds) {
        LocalDateTime now = LocalDateTime.now();
        String customerType = customerQueryService.typeOf(customerId);
        List<Long> skus = skuIds == null ? List.of() : skuIds.stream()
                .filter(id -> id != null && id > 0L).distinct().toList();
        // SPU 映射懒查：券列表里只要没有「按 SPU 限定」的券，就一次商品库都不用打
        AtomicReference<Map<Long, Long>> spuCache = new AtomicReference<>();
        return couponMapper.selectByCustomer(customerId).stream()
                .filter(po -> {
                    Coupon coupon = CouponAssembler.toDomain(po);
                    // 只判可用性，行金额传 0：门槛按整单 goodsCent 判（与前端入参同口径）
                    List<Coupon.Line> rows = skus.stream()
                            .map(skuId -> new Coupon.Line(skuId,
                                    spuIdsFor(coupon, skus, spuCache).get(skuId), 0L))
                            .toList();
                    return coupon.applicability(customerType, goodsCent, rows, now).usable();
                })
                .map(po -> toVO(po, now))
                .toList();
    }

    /** SPU 映射按请求懒加载一次：品类券之外不查，且同一次列表请求内只查一次。 */
    private Map<Long, Long> spuIdsFor(Coupon coupon, List<Long> skus,
                                      AtomicReference<Map<Long, Long>> cache) {
        if (!Coupon.SCOPE_SPU.equals(coupon.scopeType())) {
            return Map.of();
        }
        Map<Long, Long> loaded = cache.get();
        if (loaded == null) {
            loaded = productQueryService.spuIdsOfSkus(skus);
            cache.set(loaded);
        }
        return loaded;
    }

    @Override
    public List<CouponDtos.TemplateVO> listTemplates() {
        return templateMapper.selectAdminTemplates().stream()
                .map(po -> new CouponDtos.TemplateVO(po.getId(), po.getName(), po.getAmountCent(),
                        po.getThresholdCent(), po.getValidDays(), po.getCouponType(),
                        po.getDiscountType(), po.getDiscountRate(), po.getScopeType(),
                        po.getScopeIds(), po.getCustomerScope()))
                .toList();
    }

    @Override
    public List<CouponDtos.IssueVO> listRecentIssues(int limit) {
        int safe = Math.max(1, Math.min(limit, 100));
        return couponMapper.selectRecentIssues(safe).stream()
                .map(po -> new CouponDtos.IssueVO(po.getId(), po.getCustomerId(), po.getCouponNo(),
                        po.getName(), po.getAmountCent(), po.getThresholdCent(), po.getSource(),
                        po.getReason(), toMillis(po.getCreatedAt()), po.getCouponType(),
                        po.getDiscountType(), po.getDiscountRate()))
                .toList();
    }

    @Override
    public List<CouponDtos.ClaimableVO> listClaimable(long customerId) {
        boolean enterprise = Coupon.CUSTOMER_ENTERPRISE.equals(customerQueryService.typeOf(customerId));
        Set<Long> claimed = customerId <= 0L ? Set.of()
                : new HashSet<>(couponMapper.selectClaimedTemplateIds(customerId));
        return templateMapper.selectClaimable().stream()
                // 企业专属券对个人客户不展示：个人领了也用不了，露出来只是干扰
                .filter(po -> enterprise || !Coupon.CUSTOMER_ENTERPRISE.equals(po.getCustomerScope()))
                .map(po -> new CouponDtos.ClaimableVO(po.getId(), po.getName(), po.getAmountCent(),
                        po.getThresholdCent(), po.getCouponType(), po.getDiscountType(),
                        po.getDiscountRate(), po.getScopeType(), po.getScopeIds(),
                        po.getCustomerScope(), po.getValidDays(), remainQty(po),
                        claimed.contains(po.getId()),
                        MemberDayPolicy.claimValidDays(po.getCouponType(), po.getAmountCent(),
                                po.getValidDays())))
                .toList();
    }

    @Override
    public PageResult<CouponDtos.TemplatePageVO> listTemplatePage(PageQuery pageQuery, String couponType,
                                                                 String keyword) {
        Page<CouponTemplatePO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        String type = couponType == null || couponType.isBlank() ? null : couponType.trim();
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        IPage<CouponTemplatePO> rows = templateMapper.selectTemplatePage(page, type, kw);
        List<CouponDtos.TemplatePageVO> list = rows.getRecords().stream()
                .map(po -> new CouponDtos.TemplatePageVO(po.getId(), po.getTemplateNo(), po.getName(),
                        po.getCouponType(), po.getDiscountType(), po.getDiscountRate(),
                        po.getAmountCent(), po.getThresholdCent(), po.getScopeType(), po.getScopeIds(),
                        po.getCustomerScope(), po.getValidDays(), po.getClaimable(), po.getTotalQty(),
                        po.getClaimedQty(), po.getStatus(),
                        po.getCreatedBy() == null ? 0L : po.getCreatedBy(),
                        po.getCreatorName(), toMillis(po.getCreatedAt())))
                .toList();
        return PageResult.of(list, templateMapper.countTemplates(type, kw),
                pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public PageResult<CouponDtos.IssuePageVO> listIssuePage(PageQuery pageQuery, String source,
                                                           String keyword) {
        Page<CustomerCouponPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        String src = source == null || source.isBlank() ? null : source.trim();
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        IPage<CustomerCouponPO> rows = couponMapper.selectIssuePage(page, src, kw);
        LocalDateTime now = LocalDateTime.now();
        List<CouponDtos.IssuePageVO> list = rows.getRecords().stream()
                .map(po -> new CouponDtos.IssuePageVO(po.getId(), po.getCustomerId(),
                        po.getCustomerName(), po.getCouponNo(), po.getName(), po.getCouponType(),
                        po.getDiscountType(), po.getDiscountRate(), po.getAmountCent(),
                        po.getThresholdCent(), derivedStatus(po, now), po.getSource(), po.getReason(),
                        toMillis(po.getExpireAt()), toMillis(po.getCreatedAt())))
                .toList();
        return PageResult.of(list, couponMapper.countIssues(src, kw),
                pageQuery.getPage(), pageQuery.getSize());
    }

    /** 过期态是派生值：库里只保留 UNUSED/USED，读时按 expire_at 判定，避免定时任务 */
    private static String derivedStatus(CustomerCouponPO po, LocalDateTime now) {
        return Coupon.UNUSED.equals(po.getStatus())
                && po.getExpireAt() != null && po.getExpireAt().isBefore(now)
                ? Coupon.EXPIRED : po.getStatus();
    }

    private static int remainQty(CouponTemplatePO po) {
        if (po.getTotalQty() < 0) return -1;
        return Math.max(0, po.getTotalQty() - po.getClaimedQty());
    }

    private static CouponDtos.CouponVO toVO(CustomerCouponPO po, LocalDateTime now) {
        String status = derivedStatus(po, now);
        return new CouponDtos.CouponVO(po.getId(), po.getCustomerId(), po.getCouponNo(), po.getName(),
                po.getAmountCent(), po.getThresholdCent(), status, po.getSource(), po.getReason(),
                toMillis(po.getExpireAt()), toMillis(po.getUsedAt()), po.getOrderNo(),
                po.getCouponType(), po.getDiscountType(), po.getDiscountRate(), po.getScopeType(),
                po.getScopeIds(), po.getCustomerScope());
    }

    private static long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
