package com.sent.sports.coupon;

import com.sent.sports.auth.service.RegisterUseCase;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.service.RegisterCouponGrantUseCase;
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

/** 新用户注册自动到账 3 张券：5 无门槛 / 10 满 20 / 50 满 80。 */
class RegisterCouponGrantUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private MessageSendService messageSendService;

    @InjectMocks
    private RegisterCouponGrantUseCase useCase;

    @Test
    void grantGivesExactlyThreeRegisterCouponsAndOneInbox() {
        when(templateMapper.selectRegisterGifts()).thenReturn(List.of(
                template(1L, "新用户无门槛券", 500, 0),
                template(2L, "新用户满20减10", 1000, 2000),
                template(3L, "新用户满80减50", 5000, 8000)));

        useCase.grant(9002L);

        verify(couponMapper, times(3)).insert(any());
        verify(messageSendService, times(1)).send(any());
    }

    @Test
    void grantMessageListsAllThreeCoupons() {
        when(templateMapper.selectRegisterGifts()).thenReturn(List.of(
                template(1L, "新用户无门槛券", 500, 0),
                template(2L, "新用户满20减10", 1000, 2000),
                template(3L, "新用户满80减50", 5000, 8000)));

        useCase.grant(9002L);

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        InboxMessage message = captor.getValue();
        assertEquals("PROMO", message.msgType());
        assertEquals("COUPON", message.bizType());
        String content = message.content();
        assertTrue(content.contains("5元"), "文案列出 5 元券");
        assertTrue(content.contains("10元"), "文案列出 10 元券");
        assertTrue(content.contains("50元"), "文案列出 50 元券");
    }

    @Test
    void grantIsNoopWithoutTemplateSeed() {
        when(templateMapper.selectRegisterGifts()).thenReturn(List.of());

        useCase.grant(9002L);

        verify(couponMapper, times(0)).insert(any());
        verify(messageSendService, times(0)).send(any());
    }

    private static CouponTemplatePO template(long id, String name, long amountCent, long thresholdCent) {
        CouponTemplatePO po = new CouponTemplatePO();
        po.setId(id);
        po.setName(name);
        po.setAmountCent(amountCent);
        po.setThresholdCent(thresholdCent);
        po.setValidDays(30);
        return po;
    }
}
