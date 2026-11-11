package com.sent.sports.member;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.mapper.MemberOverviewMapper;
import com.sent.sports.member.mapper.po.MemberSnapshotPO;
import com.sent.sports.member.service.impl.MemberQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 会员概览读面（TDD 红）：此前 level / points / credit 三个方法恒抛 40401，
 * 管理端「会员与积分」顶部三张卡永远显示 {@code --}，运营也看不出当前在看哪个账号。
 * <p>现在改为真读 {@code customer} + {@code enterprise_profile} + {@code receivable}，
 * 并把「当前账号」一并带回首屏。</p>
 */
@ExtendWith(MockitoExtension.class)
class MemberQueryServiceImplTest {

    private static final long CUSTOMER_ID = 91203L;

    @Mock
    private MemberOverviewMapper memberOverviewMapper;

    @InjectMocks
    private MemberQueryServiceImpl service;

    private static MemberSnapshotPO snapshot(String levelCode, long growth, long points) {
        MemberSnapshotPO po = new MemberSnapshotPO();
        po.setCustomerId(CUSTOMER_ID);
        po.setNickname("张伟");
        po.setPhone("13800138000");
        po.setType("PERSON");
        po.setLevelCode(levelCode);
        po.setGrowthValue(growth);
        po.setPointsBalance(points);
        po.setCreditLimitCent(null);
        po.setCreditUsedCent(null);
        po.setEnterpriseStatus(null);
        return po;
    }

    @Test
    @DisplayName("等级进度：读真库 level_code + growth_value，并算出下一档门槛与进度")
    void levelProgressReadsRealCustomer() {
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV2", 6000L, 1200L));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertNotNull(vo);
        assertEquals(CUSTOMER_ID, vo.customerId());
        assertEquals("LV2", vo.level());
        assertEquals(6000L, vo.growthValue());
        assertEquals(5000L, vo.currentThreshold());
        assertEquals(20000L, vo.nextThreshold());
        assertEquals(0.0667d, Math.round(vo.progress() * 10000d) / 10000d);
    }

    @Test
    @DisplayName("等级进度：最高档不再有下一档，进度给 1.0")
    void levelProgressTopTierIsFull() {
        when(memberOverviewMapper.selectSnapshot(CUSTOMER_ID))
                .thenReturn(snapshot("LV3", 26000L, 0L));

        MemberDtos.LevelVO vo = service.levelProgress(CUSTOMER_ID);

        assertEquals("LV3", vo.level());
        assertEquals(20000L, vo.currentThreshold());
        assertEquals(20000L, vo.nextThreshold());
        assertEquals(1.0d, vo.progress());
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
}
