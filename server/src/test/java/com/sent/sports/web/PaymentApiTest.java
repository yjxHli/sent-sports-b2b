package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.payment.controller.PaymentController;
import com.sent.sports.payment.dto.PaymentDtos;
import com.sent.sports.payment.gateway.PayGateway;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.PaymentQueryService;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.payment.service.dto.PayCallbackResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付接口契约测试（P4-17，红→绿）。
 * 发起返回 txnNo + 通道签名参数 200、回调验签失败 400(PARAM_INVALID 40001)、重复回调仍 200、支付结果轮询 PENDING。
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentApiTest extends BaseApiTest {

    @MockBean
    private CreatePaymentUseCase createPaymentUseCase;

    @MockBean
    private PayCallbackUseCase payCallbackUseCase;

    @MockBean
    private PaymentQueryService paymentQueryService;

    @MockBean
    private PayGateway payGateway;

    @Test
    @DisplayName("发起支付返回流水号 + 通道签名参数 200")
    void createReturnsTxnNo200() throws Exception {
        when(createPaymentUseCase.create(any()))
                .thenReturn(new CreatePaymentResult("T1", "CREATED", 10000L, "O1"));
        Map<String, String> notify = new LinkedHashMap<>();
        notify.put("txnNo", "T1");
        notify.put("orderNo", "O1");
        notify.put("amountCent", "10000");
        when(payGateway.create("T1", "O1", 10000L, "MOCK"))
                .thenReturn(new PayGateway.PayTicket("T1", notify, "SIGN-64"));

        mvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderNo", "O1", "amountCent", 10000L,
                                "channel", "MOCK", "idemKey", "idem-p1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.txnNo").value("T1"))
                .andExpect(jsonPath("$.data.amountCent").value(10000))
                .andExpect(jsonPath("$.data.amountYuan").value("100.00"))
                .andExpect(jsonPath("$.data.sign").value("SIGN-64"))
                .andExpect(jsonPath("$.data.notifyParams.orderNo").value("O1"));
    }

    @Test
    @DisplayName("回调验签失败返回 40001")
    void callbackSignInvalidReturns40001() throws Exception {
        when(payCallbackUseCase.handle(any()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "支付回调验签失败"));
        mvc.perform(post("/api/payment/mock/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("TX1", "O1", 10000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("渠道通知入口固定为 /payment/callback，同样走验签")
    void productionCallbackEndpointAlsoVerifies() throws Exception {
        when(payCallbackUseCase.handle(any()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "支付回调验签失败"));
        mvc.perform(post("/api/payment/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("TX1", "O1", 10000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("重复回调仍返回 200（幂等）")
    void duplicateCallbackStill200() throws Exception {
        when(payCallbackUseCase.handle(any())).thenReturn(PayCallbackResult.alreadyPaid(
                new PayCallbackCmd("TX1", "O1", 10000L, "MOCK", "CH1", 0L)));
        String body = callbackBody("TX1", "O1", 10000L);
        mvc.perform(post("/api/payment/mock/callback")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_PAID"));
        mvc.perform(post("/api/payment/mock/callback")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_PAID"));
        verify(payCallbackUseCase, times(2)).handle(any());
    }

    @Test
    @DisplayName("支付结果轮询返回 PENDING 200")
    void payResultReturnsPending200() throws Exception {
        when(paymentQueryService.getPayResult("O1"))
                .thenReturn(new PaymentDtos.PayResultVO("O1", "PENDING", null, null, null));
        mvc.perform(get("/api/orders/O1/pay-result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("我的支付流水返回空分页 200")
    void listMyPaymentsReturnsEmptyPage() throws Exception {
        when(paymentQueryService.listMyPayments(any())).thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/payments").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** 渠道通知报文：一组键值 + 签名（缺签名/签名错时用例抛 PARAM_INVALID）。 */
    private String callbackBody(String txnNo, String orderNo, long amountCent) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnNo", txnNo);
        params.put("orderNo", orderNo);
        params.put("amountCent", String.valueOf(amountCent));
        params.put("channel", "MOCK");
        params.put("payChannelTradeNo", "CH1");
        params.put("paidAtMillis", "0");
        params.put("sign", "FORGED");
        return toJson(params);
    }
}
