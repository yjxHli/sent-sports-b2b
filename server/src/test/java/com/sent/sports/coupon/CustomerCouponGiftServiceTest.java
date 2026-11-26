package com.sent.sports.coupon;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.CouponIssueUseCase;
import com.sent.sports.coupon.service.dto.IssueCouponCmd;
import com.sent.sports.coupon.service.impl.CustomerCouponGiftServiceImpl;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服发券服务测试（TDD 红，V64）。
 *
 * <p>客服发券与管理端发券是<b>两条不同的权力</b>：运营能按活动全量发大额券，
 * 客服只能在工单里补一张小额券安抚客户。所以这里锁三件事：</p>
 * <ol>
 *   <li><b>20 元是硬上限</b>，而且由服务端判定——前端下拉不过滤只是一种体验，
 *       拿 curl 直接打接口也必须被拦住；</li>
 *   <li><b>只能发一张、只能发给这个客户</b>，不能顺手全量发放；</li>
 *   <li><b>发完要有痕迹</b>：source=AFTER_SALE + reason，后续复盘「补偿发了多少」靠这两个字段。</li>
 * </ol>
 */
class CustomerCouponGiftServiceTest extends BaseUseCaseTest {

    private static final long CUSTOMER = 9002L;
    private static final long OPERATOR = 9003L;
    /** 客服单张券面额上限：20 元 = 2000 分。 */
    private static final long CAP_CENT = 2000L;

    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CouponIssueUseCase issueUseCase;
    @Mock
    private CustomerQueryService customerQueryService;

    @InjectMocks
    private CustomerCouponGiftServiceImpl service;

    private CouponTemplatePO template(long id, String name, long amountCent) {
        return template(id, name, amountCent, 0L, "CS_GIFT");
    }

    private CouponTemplatePO template(long id, String name, long amountCent, long thresholdCent, String scene) {
        CouponTemplatePO po = new CouponTemplatePO();
        po.setId(id);
        po.setName(name);
        po.setAmountCent(amountCent);
        po.setThresholdCent(thresholdCent);
        po.setValidDays(30);
        po.setCouponType("NO_THRESHOLD");
        po.setDiscountType("AMOUNT");
        po.setDiscountRate(100);
        po.setScopeType("ALL");
        po.setCustomerScope("ALL");
        po.setScene(scene);
        return po;
    }

    /**
     * 2026-11-26 用户要求：客服工作台只能发 5 档——5/8/10/15/20 元，且全部无门槛。
     * 运营在管理端自建的有门槛券、折扣券、大额券都不能混进这个下拉。
     */
    @Test
    @DisplayName("可发券档位：只给客服专用 5 档无门槛券")
    void giftOptionsOnlyCsGiftNoThreshold() {
        when(templateMapper.selectTemplates(null, "CS_GIFT")).thenReturn(List.of(
                template(31L, "客服补偿券 5 元", 500L),
                template(32L, "客服补偿券 8 元", 800L),
                template(33L, "客服补偿券 10 元", 1000L),
                template(34L, "客服补偿券 15 元", 1500L),
                template(35L, "客服补偿券 20 元", 2000L),
                // 脏数据兜底：即使 scene 标了 CS_GIFT，有门槛/超额也不进下拉
                template(36L, "脏数据-有门槛", 1000L, 5000L, "CS_GIFT"),
                template(37L, "脏数据-超额", 3000L, 0L, "CS_GIFT")));

        List<CouponDtos.TemplateVO> options = service.giftOptions();

        assertEquals(5, options.size());
        assertEquals(List.of(500L, 800L, 1000L, 1500L, 2000L),
                options.stream().map(CouponDtos.TemplateVO::amountCent).toList());
        assertTrue(options.stream().allMatch(vo -> vo.thresholdCent() == 0L), "客服券必须无门槛");
        assertTrue(options.stream().allMatch(vo -> vo.amountCent() <= CAP_CENT));
        verify(templateMapper, never()).selectAdminTemplates();
    }

    @Test
    @DisplayName("发券：运营券即使面额 ≤20 元也不能从客服通道发")
    void giftRejectsNonCsGiftTemplate() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, "5元券", 500L, 0L, "ADMIN"));

        BizException e = assertThrows(BizException.class,
                () -> service.gift(CUSTOMER, 10L, "客户投诉安抚", OPERATOR));

        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(issueUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("发券：超过 20 元直接拒绝（服务端硬拦截，不靠前端隐藏）")
    void giftRejectsOverCap() {
        when(templateMapper.selectById(2L)).thenReturn(template(2L, "100 元大额券", 10000L));

        BizException over = assertThrows(BizException.class,
                () -> service.gift(CUSTOMER, 2L, "客户投诉安抚", OPERATOR));

        assertEquals(ErrorCode.PARAM_INVALID, over.getErrorCode());
        assertTrue(over.resolvedMessage().contains("20"), "要说明上限是 20 元");
        verify(issueUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("发券：券模板不存在 404；客户不存在 404，都不发放")
    void giftRejectsUnknowns() {
        when(templateMapper.selectById(404L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class,
                () -> service.gift(CUSTOMER, 404L, "安抚", OPERATOR)).getErrorCode());

        when(templateMapper.selectById(1L)).thenReturn(template(1L, "20 元补偿券", 2000L));
        when(customerQueryService.typeOf(404L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class,
                () -> service.gift(404L, 1L, "安抚", OPERATOR)).getErrorCode());

        verify(issueUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("发券：只发给这一个客户，source=AFTER_SALE，原因写进券与站内信")
    void giftIssuesToSingleCustomer() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, "20 元补偿券", 2000L));
        when(customerQueryService.typeOf(CUSTOMER)).thenReturn("PERSONAL");
        when(issueUseCase.issue(any())).thenReturn(1);

        CouponDtos.GiftResultVO result = service.gift(CUSTOMER, 1L, " 客户投诉安抚 ", OPERATOR);

        assertEquals(1, result.issued());
        assertEquals("20 元补偿券", result.name());
        assertEquals(2000L, result.amountCent());

        ArgumentCaptor<IssueCouponCmd> captor = ArgumentCaptor.forClass(IssueCouponCmd.class);
        verify(issueUseCase).issue(captor.capture());
        IssueCouponCmd cmd = captor.getValue();
        assertEquals(1L, cmd.templateId());
        assertEquals(List.of(CUSTOMER), cmd.customerIds(), "客服只能发当前会话这一个客户");
        assertEquals("AFTER_SALE", cmd.scene(), "来源必须是售后补偿，不能混进平台活动口径");
        assertEquals("客户投诉安抚", cmd.reason(), "原因要去空格后落库");
    }

    @Test
    @DisplayName("发券：原因留空按「客服发放」兜底，不允许空串写进站内信")
    void giftFallsBackForBlankReason() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, "20 元补偿券", 2000L));
        when(customerQueryService.typeOf(CUSTOMER)).thenReturn("PERSONAL");
        when(issueUseCase.issue(any())).thenReturn(1);

        service.gift(CUSTOMER, 1L, "   ", OPERATOR);

        ArgumentCaptor<IssueCouponCmd> captor = ArgumentCaptor.forClass(IssueCouponCmd.class);
        verify(issueUseCase).issue(captor.capture());
        assertEquals("客服发放", captor.getValue().reason());
    }

    @Test
    @DisplayName("发放记录：按客户取最近 N 条，limit 收口在 1~50")
    void recordsClampLimit() {
        CustomerCouponPO row = new CustomerCouponPO();
        row.setId(11L);
        row.setCouponNo("CPN1");
        row.setName("20 元补偿券");
        row.setAmountCent(2000L);
        row.setThresholdCent(0L);
        row.setStatus("UNUSED");
        row.setSource("AFTER_SALE");
        row.setReason("客户投诉安抚");
        row.setExpireAt(LocalDateTime.now().plusDays(30));
        row.setCreatedAt(LocalDateTime.now());
        when(couponMapper.selectGiftRecords(anyLong(), anyInt())).thenReturn(List.of(row));

        List<CouponDtos.GiftRecordVO> records = service.recordsOf(CUSTOMER, 999);

        assertEquals(1, records.size());
        assertEquals("CPN1", records.get(0).couponNo());
        assertEquals("AFTER_SALE", records.get(0).source());
        assertEquals("客户投诉安抚", records.get(0).reason());
        verify(couponMapper).selectGiftRecords(CUSTOMER, 50);

        service.recordsOf(CUSTOMER, 0);
        verify(couponMapper).selectGiftRecords(CUSTOMER, 1);
    }
}
