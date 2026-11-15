package com.sent.sports.product;

import com.sent.sports.product.mapper.MemberExclusiveMapper;
import com.sent.sports.product.mapper.po.ExclusiveProductRow;
import com.sent.sports.product.service.MemberExclusiveService;
import com.sent.sports.product.service.impl.MemberExclusiveServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 会员专属商品合并逻辑（2026-11-16，TDD 红）。
 *
 * <p>「专属」有两个来源，合并时三条规则不能错：</p>
 * <ul>
 *   <li>一客一价排前面（谈过的价最硬）；</li>
 *   <li>等级门槛只能「够得着」才出现，LV1 会员不该看到 LV3 专属商品；</li>
 *   <li>同一商品两边都命中时只留一客一价那行，避免页面上出现两张一样的卡片。</li>
 * </ul>
 */
class MemberExclusiveServiceTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;

    @Mock
    private MemberExclusiveMapper exclusiveMapper;

    private MemberExclusiveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MemberExclusiveServiceImpl(exclusiveMapper);
    }

    private ExclusiveProductRow row(long productId, String minLevel, String source, long priceCent) {
        ExclusiveProductRow r = new ExclusiveProductRow();
        r.setProductId(productId);
        r.setProductName("商品" + productId);
        r.setMainImage("2026/11/x/" + productId + ".jpg");
        r.setMinLevel(minLevel);
        r.setSource(source);
        r.setPriceCent(priceCent);
        return r;
    }

    @Test
    @DisplayName("LV3 会员：一客一价在前，LV1/LV2/LV3 门槛全部命中")
    void lv3SeesAllLevels() {
        when(exclusiveMapper.selectAgreementProducts(CUSTOMER_ID))
                .thenReturn(List.of(row(9104L, null, "AGREEMENT", 4800L)));
        when(exclusiveMapper.selectLevelProducts()).thenReturn(List.of(
                row(9101L, "LV1", "LEVEL", 39900L),
                row(9108L, "LV2", "LEVEL", 29900L),
                row(9105L, "LV3", "LEVEL", 58390L)));

        List<MemberExclusiveService.ExclusiveProduct> list = service.listFor(CUSTOMER_ID, 3);

        assertEquals(4, list.size());
        assertEquals(9104L, list.get(0).productId(), "一客一价必须排第一");
        assertEquals("AGREEMENT", list.get(0).source());
        assertEquals(4800L, list.get(0).priceCent());
    }

    @Test
    @DisplayName("LV1 会员：只看到 LV1 门槛的商品，LV2/LV3 不出现")
    void lv1SeesOnlyLv1() {
        when(exclusiveMapper.selectAgreementProducts(CUSTOMER_ID)).thenReturn(List.of());
        when(exclusiveMapper.selectLevelProducts()).thenReturn(List.of(
                row(9101L, "LV1", "LEVEL", 39900L),
                row(9108L, "LV2", "LEVEL", 29900L),
                row(9105L, "LV3", "LEVEL", 58390L)));

        List<MemberExclusiveService.ExclusiveProduct> list = service.listFor(CUSTOMER_ID, 1);

        assertEquals(1, list.size());
        assertEquals(9101L, list.get(0).productId());
    }

    @Test
    @DisplayName("同一商品两边都命中：只留一客一价那行")
    void agreementWinsOnDuplicate() {
        when(exclusiveMapper.selectAgreementProducts(CUSTOMER_ID))
                .thenReturn(List.of(row(9105L, null, "AGREEMENT", 50000L)));
        when(exclusiveMapper.selectLevelProducts())
                .thenReturn(List.of(row(9105L, "LV3", "LEVEL", 58390L)));

        List<MemberExclusiveService.ExclusiveProduct> list = service.listFor(CUSTOMER_ID, 3);

        assertEquals(1, list.size());
        assertEquals("AGREEMENT", list.get(0).source());
        assertEquals(50000L, list.get(0).priceCent());
    }

    @Test
    @DisplayName("未登录（customerId=0）：跳过一客一价查询，只给等级专属")
    void anonymousSkipsAgreementQuery() {
        when(exclusiveMapper.selectLevelProducts()).thenReturn(List.of(row(9101L, "LV1", "LEVEL", 39900L)));

        List<MemberExclusiveService.ExclusiveProduct> list = service.listFor(0L, 3);

        assertEquals(1, list.size());
        org.mockito.Mockito.verify(exclusiveMapper, org.mockito.Mockito.never())
                .selectAgreementProducts(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("门槛值脏数据（未知等级码）：按「无门槛」跳过，不让脏数据把商品漏出来")
    void unknownLevelCodeSkipped() {
        when(exclusiveMapper.selectAgreementProducts(CUSTOMER_ID)).thenReturn(List.of());
        when(exclusiveMapper.selectLevelProducts()).thenReturn(List.of(
                row(9101L, "VIP9", "LEVEL", 39900L),
                row(9102L, null, "LEVEL", 19900L)));

        assertTrue(service.listFor(CUSTOMER_ID, 3).isEmpty());
    }

    @Test
    @DisplayName("超过 12 条截断：会员中心一屏最多两行半")
    void capsAtTwelve() {
        when(exclusiveMapper.selectAgreementProducts(CUSTOMER_ID)).thenReturn(List.of());
        List<ExclusiveProductRow> many = new java.util.ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            many.add(row(9000L + i, "LV1", "LEVEL", 1000L * i));
        }
        when(exclusiveMapper.selectLevelProducts()).thenReturn(many);

        assertEquals(12, service.listFor(CUSTOMER_ID, 3).size());
    }
}
