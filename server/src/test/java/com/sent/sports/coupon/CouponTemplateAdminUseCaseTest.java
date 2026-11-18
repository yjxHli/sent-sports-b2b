package com.sent.sports.coupon.service;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.service.dto.CreateCouponTemplateCmd;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端自建券模板（2026-11-18 用户需求 2）。
 *
 * <p>创建时最容易出错的三处：品类券没指定商品（等于全场券）、折扣券写成 100（等于没打折）、
 * 企业专属券落库成所有人可用。删除必须走 BatchIds 归一化，否则确认令牌对不上。</p>
 */
class CouponTemplateAdminUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private IdGen idGen;

    @InjectMocks
    private CouponTemplateAdminUseCase useCase;

    @Test
    void categoryTemplateRequiresScopeIds() {
        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd(
                "斯伯丁专享9折", Coupon.TYPE_CATEGORY, Coupon.DISCOUNT_PERCENT, 0L, 0L, 90,
                Coupon.SCOPE_SPU, null), 9001L));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("商品"), "文案要点明必须选择适用商品");
        verify(templateMapper, never()).insert(any());
    }

    @Test
    void percentTemplateRequiresRealDiscountRate() {
        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd(
                "斯伯丁专享", Coupon.TYPE_CATEGORY, Coupon.DISCOUNT_PERCENT, 0L, 0L, 100,
                Coupon.SCOPE_SPU, "9101"), 9001L));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("折"));
    }

    @Test
    void enterpriseTemplateAlwaysScopedToEnterpriseCustomers() {
        when(idGen.next("CPT")).thenReturn("CPT20261118000001");
        when(templateMapper.insert(any())).thenReturn(1);

        // 运营在界面上把「适用客户」留成 ALL，也必须按企业专属券落库
        useCase.create(cmd("企业专享满1000减100", Coupon.TYPE_ENTERPRISE, Coupon.DISCOUNT_AMOUNT,
                10000L, 100000L, 100, Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ALL), 9001L);

        ArgumentCaptor<CouponTemplatePO> captor = ArgumentCaptor.forClass(CouponTemplatePO.class);
        verify(templateMapper).insert(captor.capture());
        CouponTemplatePO po = captor.getValue();
        assertEquals(Coupon.CUSTOMER_ENTERPRISE, po.getCustomerScope());
        assertEquals(Coupon.TYPE_ENTERPRISE, po.getCouponType());
    }

    @Test
    void createdTemplateCarriesCouponNoAndCreator() {
        when(idGen.next("CPT")).thenReturn("CPT20261118000002");
        when(templateMapper.insert(any())).thenReturn(1);

        useCase.create(cmd("满20减10券", Coupon.TYPE_FULL_REDUCTION, Coupon.DISCOUNT_AMOUNT,
                1000L, 2000L, 100, Coupon.SCOPE_ALL, null), 9003L);

        ArgumentCaptor<CouponTemplatePO> captor = ArgumentCaptor.forClass(CouponTemplatePO.class);
        verify(templateMapper).insert(captor.capture());
        CouponTemplatePO po = captor.getValue();
        assertEquals("CPT20261118000002", po.getTemplateNo(), "券号由 IdGen 生成，前缀 CPT");
        assertEquals(9003L, po.getCreatedBy(), "创建人要落当前登录员工");
        assertEquals(0, po.getDeleted());
        assertEquals("ADMIN", po.getScene(), "自建券只在管理端发放");
        assertEquals("ACTIVE", po.getStatus());
        assertEquals(0, po.getClaimable(), "自建券默认不进领券中心");
    }

    @Test
    void deleteNormalizesIdsBeforeHittingSql() {
        when(templateMapper.batchMarkDeleted(List.of("11", "12"))).thenReturn(2);

        assertEquals(2, useCase.delete(List.of(" 12 ", "11", "12", "")));

        // 与确认令牌同口径：trim → 去空 → 去重 → 字典序
        verify(templateMapper).batchMarkDeleted(List.of("11", "12"));
    }

    private static CreateCouponTemplateCmd cmd(String name, String couponType, String discountType,
                                               long amountCent, long thresholdCent, int discountRate,
                                               String scopeType, String scopeIds) {
        return cmd(name, couponType, discountType, amountCent, thresholdCent, discountRate,
                scopeType, scopeIds, Coupon.CUSTOMER_ALL);
    }

    private static CreateCouponTemplateCmd cmd(String name, String couponType, String discountType,
                                               long amountCent, long thresholdCent, int discountRate,
                                               String scopeType, String scopeIds, String customerScope) {
        return new CreateCouponTemplateCmd(name, couponType, discountType, amountCent, thresholdCent,
                discountRate, scopeType, scopeIds, customerScope, 30, 0, -1, "ACTIVE");
    }
}
