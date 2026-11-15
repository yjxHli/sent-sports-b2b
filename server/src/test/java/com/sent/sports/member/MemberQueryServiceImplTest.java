package com.sent.sports.member;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.mapper.MemberOverviewMapper;
import com.sent.sports.member.mapper.po.MemberSnapshotPO;
import com.sent.sports.member.service.impl.MemberQueryServiceImpl;
import com.sent.sports.product.service.MemberExclusiveService;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 会员概览读面（TDD 红→绿）：此前 level / points / credit 三个方法恒抛 40401，
 * 管理端「会员与积分」顶部三张卡永远显示 {@code --}，运营也看不出当前在看哪个账号。
 * <p>现在改为真读 {@code customer} + {@code enterprise_profile} + {@code receivable}，
 * 并把「当前账号」一并带回首屏。</p>
 * <p>本类同时锁住「等级与成长值一致性」：成长值累加后 {@code level_code} 未提升时，
 * 读面必须按成长值机算有效等级（成长值 16167 → LV2），而不是拿 LV1 的下一档 5000
 * 去算进度（旧实现进度 1.0、差额 -11167）；授级到期后回落到成长值档位。</p>
 */
class MemberQueryServiceImplTest extends BaseUnitTest {

    private static final ZoneId ZONE = com.sent.sports.common.util.TimeUtil.BIZ_ZONE;

    private static final long CUSTOMER_ID = 91203L;

    @Mock
    private MemberOverviewMapper memberOverviewMapper;

    @Mock
    private MemberExclusiveService memberExclusiveService;

    @Mock
    private Clock clock;

    @InjectMocks
    private MemberQueryServiceImpl service;

    private static MemberSnapshotPO snapshot(String levelCode, long growth, long points) {
        return snapshot(levelCode, growth, points, null);
    }

    private static MemberSnapshotPO snapshot(String levelCode, long growth, long points,
                                             LocalDateTime levelValidTo) {
        MemberSnapshotPO po = new MemberSnapshotPO();
        po.setCustomerId(CUSTOMER_ID);
        po.setNickname("张伟");
        po.setPhone("13800138000");
        po.setType("PERSON");
        po.setLevelCode(levelCode);
        po.setGrowthValue(growth);
        po.setPointsBalance(points);
        po.setLevelValidTo(levelValidTo);
        po.setCreditLimitCent(null);
        po.setCreditUsedCent(null);
        po.setEnterpriseStatus(null);
        return po;
    }

    private void withNow() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
    }

    @Test
    @DisplayName("等级进度：读真库 level_code + growth_value，并算出下一档门槛与进度")
    void levelProgressReadsRealCustomer() {
        withNow();
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV2", 6000L, 1200L));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertNotNull(vo);
        assertEquals(CUSTOMER_ID, vo.customerId());
        assertEquals("LV2", vo.level());
        assertEquals(6000L, vo.growthValue());
        assertEquals(5000L, vo.currentThreshold());
        assertEquals(20000L, vo.nextThreshold());
        assertEquals(0.0667d, round4(vo.progress()));
    }

    @Test
    @DisplayName("等级进度：最高档不再有下一档，进度给 1.0")
    void levelProgressTopTierIsFull() {
        withNow();
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV3", 26000L, 0L));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertEquals("LV3", vo.level());
        assertEquals(20000L, vo.currentThreshold());
        assertEquals(20000L, vo.nextThreshold());
        assertEquals(1.0d, vo.progress());
    }

    @Test
    @DisplayName("等级进度：level_code 滞后于成长值时按成长值取档（16167 → LV2，差额 +3833）")
    void levelProgressDerivesTierFromGrowthWhenCodeStale() {
        withNow();
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV1", 16167L, 0L));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertEquals("LV2", vo.level(), "成长值 16167 已过 LV2 门槛 5000，不应继续显示 LV1");
        assertEquals(5000L, vo.currentThreshold(), "当前档门槛应取 LV2 的 5000，而不是 LV1 的 1000");
        assertEquals(20000L, vo.nextThreshold(), "下一档应是 LV3 的 20000，而不是 5000");
        assertEquals(0.7445d, round4(vo.progress()));
        assertEquals(3833L, vo.nextThreshold() - vo.growthValue(), "差额必须是正数");
        assertEquals(0L, vo.validToMillis(), "没有授级记录时有效期为 0（前端显示长期有效）");
    }

    @Test
    @DisplayName("等级进度：人工授级高于成长值时保留授级，并回传真实到期时间")
    void levelProgressKeepsGrantedTierWithExpiry() {
        withNow();
        LocalDateTime validTo = LocalDateTime.of(2024, 6, 1, 0, 0);
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV3", 4943L, 0L, validTo));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertEquals("LV3", vo.level(), "未到期的授级不因成长值偏低而降级");
        assertEquals(20000L, vo.currentThreshold());
        assertEquals(20000L, vo.nextThreshold());
        assertEquals(1.0d, vo.progress());
        assertEquals(validTo.atZone(ZONE).toInstant().toEpochMilli(), vo.validToMillis());
    }

    @Test
    @DisplayName("等级进度：授级过期后回落到成长值档位，有效期归零")
    void levelProgressFallsBackAfterGrantExpired() {
        withNow();
        LocalDateTime expired = LocalDateTime.of(2023, 6, 1, 0, 0);
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV3", 4940L, 0L, expired));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertEquals("LV1", vo.level(), "授级到期后按成长值 4940 回落 LV1");
        assertEquals(1000L, vo.currentThreshold());
        assertEquals(5000L, vo.nextThreshold());
        assertEquals(0.985d, round4(vo.progress()));
        assertEquals(0L, vo.validToMillis(), "过期后不再显示旧到期时间");
    }

    @Test
    @DisplayName("等级进度：没有授级记录的存量高等级不降级（只升不降）")
    void levelProgressNeverDowngradesWithoutGrant() {
        withNow();
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV3", 100L, 0L));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertEquals("LV3", vo.level());
        assertEquals(20000L, vo.currentThreshold());
        assertEquals(1.0d, vo.progress());
        assertEquals(0L, vo.validToMillis());
    }

    @Test
    @DisplayName("积分明细：直接读 customer.points_balance")
    void pointDetailReadsBalance() {
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV1", 1200L, 1200L));

        MemberDtos.PointVO vo = service.pointDetail(CUSTOMER_ID);

        assertEquals(CUSTOMER_ID, vo.customerId());
        assertEquals(1200L, vo.availablePoints());
    }

    @Test
    @DisplayName("额度与应收：总额度/已用/可用 + 未结应收一起返回")
    void creditCombinesProfileAndReceivable() {
        MemberSnapshotPO po = snapshot("LV2", 6000L, 1200L);
        po.setCreditLimitCent(100_000L);
        po.setCreditUsedCent(30_000L);
        po.setEnterpriseStatus("ACTIVE");
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID)).thenReturn(po);
        when(memberOverviewMapper.sumOpenReceivable(CUSTOMER_ID)).thenReturn(30_000L);

        MemberDtos.CreditVO vo = service.credit(CUSTOMER_ID);

        assertEquals(100_000L, vo.creditLimitCent());
        assertEquals("1000.00", vo.creditLimitYuan());
        assertEquals(30_000L, vo.creditUsedCent());
        assertEquals(70_000L, vo.availableCent());
        assertEquals(30_000L, vo.receivableCent());
        assertEquals("300.00", vo.receivableYuan());
    }

    @Test
    @DisplayName("个人客户没有企业档案：额度归零而不是报错")
    void creditWorksForPersonalCustomer() {
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("NORMAL", 100L, 50L));
        when(memberOverviewMapper.sumOpenReceivable(CUSTOMER_ID)).thenReturn(0L);

        MemberDtos.CreditVO vo = service.credit(CUSTOMER_ID);

        assertEquals(0L, vo.creditLimitCent());
        assertEquals(0L, vo.availableCent());
        assertEquals(0L, vo.receivableCent());
    }

    @Test
    @DisplayName("客户不存在：40401，避免静默返回空卡片")
    void missingCustomerThrowsNotFound() {
        when(memberOverviewMapper.selectSnapshot(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.levelProgress(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 会员专属商品（2026-11-16，TDD 红）：此前恒返回空列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("专属商品：按有效等级序取数，金额分→元映射，主图与来源一并回传")
    void exclusiveProductsUsesEffectiveTier() {
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV3", 21867L, 0L));
        when(memberExclusiveService.listFor(CUSTOMER_ID, 3)).thenReturn(List.of(
                new MemberExclusiveService.ExclusiveProduct(9104L, "尤尼克斯 AS-05", "2026/11/aa/x.jpg",
                        4800L, "AGREEMENT"),
                new MemberExclusiveService.ExclusiveProduct(9105L, "专业定制球衣", null, 58390L, "LEVEL")));

        List<MemberDtos.ExclusiveProductVO> list = service.exclusiveProducts(CUSTOMER_ID);

        assertEquals(2, list.size());
        assertEquals("9104", list.get(0).productId());
        assertEquals("尤尼克斯 AS-05", list.get(0).productName());
        assertEquals(4800L, list.get(0).priceCent());
        assertEquals("48.00", list.get(0).priceYuan());
        assertEquals("2026/11/aa/x.jpg", list.get(0).mainImage());
        assertEquals("AGREEMENT", list.get(0).source());
        assertEquals("LEVEL", list.get(1).source());
    }

    @Test
    @DisplayName("专属商品：等级未达门槛时按较低等级序取数（成长值 1200 → LV1 序 1）")
    void exclusiveProductsLowTier() {
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV1", 1200L, 0L));
        when(memberExclusiveService.listFor(CUSTOMER_ID, 1)).thenReturn(List.of());

        assertTrue(service.exclusiveProducts(CUSTOMER_ID).isEmpty());
    }

    @Test
    @DisplayName("专属商品：客户不存在仍抛 40401")
    void exclusiveProductsMissingCustomer() {
        when(memberOverviewMapper.selectSnapshot(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.exclusiveProducts(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
