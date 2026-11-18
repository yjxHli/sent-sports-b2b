package com.sent.sports.coupon;

import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.CouponIssueUseCase;
import com.sent.sports.coupon.service.dto.IssueCouponCmd;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理端发券：指定用户 / 全员发放，并按场景写不同口径的站内信。 */
class CouponIssueUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private MessageSendService messageSendService;

    @InjectMocks
    private CouponIssueUseCase useCase;

    @Test
    void activityIssueToSpecifiedCustomerSendsPromoMessage() {
        when(templateMapper.selectById(12L)).thenReturn(template(12L, "10元券", 1000));

        IssueCouponCmd cmd = new IssueCouponCmd(12L, List.of(9002L), 5000L, null, "ACTIVITY");
        int issued = useCase.issue(cmd);

        assertEquals(1, issued);
        verify(couponMapper, times(1)).insert(any());

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        InboxMessage message = captor.getValue();
        assertEquals(9002L, message.customerId());
        assertEquals("PROMO", message.msgType());
        assertEquals("COUPON", message.bizType());
        assertTrue(message.content().contains("平台活动"), "活动发放文案应以「平台活动」开头");
        assertTrue(message.content().contains("10元券"), "文案带券名");
    }

    @Test
    void afterSaleIssueUsesDefaultReasonWhenBlank() {
        when(templateMapper.selectById(16L)).thenReturn(template(16L, "50元券", 5000));

        useCase.issue(new IssueCouponCmd(16L, List.of(9002L), 20000L, "  ", "AFTER_SALE"));

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        String content = captor.getValue().content();
        assertTrue(content.contains("平台补充"), "售后补券文案应以「平台补充」开头");
        assertTrue(content.contains("客服售后补偿"), "未填原因时用默认话术");
    }

    @Test
    void issueToEveryoneUsesAllCustomerIdsAndSkipsMessageWhenNoRecipient() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, "5元券", 500));
        when(templateMapper.selectAllCustomerIds()).thenReturn(List.of(9002L, 91001L));

        int issued = useCase.issue(new IssueCouponCmd(10L, List.of(), null, "会员日", "ACTIVITY"));

        assertEquals(2, issued);
        verify(couponMapper, times(2)).insert(any());
        verify(messageSendService, times(2)).send(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("门槛可自行设定：null 沿用模板，0 才是真正的无门槛")
    void thresholdOverrideDistinguishesNullFromZero() {
        when(templateMapper.selectById(30L)).thenReturn(template(30L, "10元券", 1000, 5000));

        // 显式传 0：必须覆盖模板的 50 元门槛，不能退回模板默认
        useCase.issue(new IssueCouponCmd(30L, List.of(9002L), 0L, null, "ACTIVITY"));
        ArgumentCaptor<CustomerCouponPO> zeroCaptor = ArgumentCaptor.forClass(CustomerCouponPO.class);
        verify(couponMapper).insert(zeroCaptor.capture());
        assertEquals(0L, zeroCaptor.getValue().getThresholdCent(),
                "门槛填 0 表示无门槛；用 0 代表「未设置」会让运营永远发不出无门槛券");

        // 不传门槛（null）：沿用模板的 50 元门槛
        useCase.issue(new IssueCouponCmd(30L, List.of(9002L), null, null, "ACTIVITY"));
        ArgumentCaptor<CustomerCouponPO> nullCaptor = ArgumentCaptor.forClass(CustomerCouponPO.class);
        verify(couponMapper, times(2)).insert(nullCaptor.capture());
        assertEquals(5000L, nullCaptor.getAllValues().get(1).getThresholdCent(),
                "未指定门槛时应沿用模板默认");
    }

    private static CouponTemplatePO template(long id, String name, long amountCent) {
        return template(id, name, amountCent, 0L);
    }

    private static CouponTemplatePO template(long id, String name, long amountCent, long thresholdCent) {
        CouponTemplatePO po = templateBase(id, name, amountCent);
        po.setThresholdCent(thresholdCent);
        return po;
    }

    private static CouponTemplatePO templateBase(long id, String name, long amountCent) {
        CouponTemplatePO po = new CouponTemplatePO();
        po.setId(id);
        po.setName(name);
        po.setAmountCent(amountCent);
        po.setValidDays(30);
        return po;
    }
}
