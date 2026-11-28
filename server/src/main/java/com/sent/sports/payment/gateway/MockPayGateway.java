package com.sent.sports.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 模拟支付网关（P5-33）：用于开发与集成测试，签名规则与真实通道同构，且<b>完全可复现</b>
 * （同样的入参与时钟 → 同样的报文与签名），所以下单→支付→回调全链路能在无外网环境下跑通。
 *
 * <h3>签名规则</h3>
 * <ol>
 *   <li>取全部参数，剔除 {@code sign} 与空值；</li>
 *   <li>按键名字典序排序，拼成 {@code k1=v1&k2=v2}（不含尾部 &）；</li>
 *   <li>HMAC-SHA256(secret) 后转大写十六进制。</li>
 * </ol>
 * 校验用 {@link java.security.MessageDigest#isEqual} 等价的常数时间比较，避免计时侧信道。
 *
 * <h3>TODO（2026-11-28 批次 2 / P1-06）：接真实支付通道时必须一起改的三件事</h3>
 * <ol>
 *   <li>{@link #create} / {@link #refund} 改成真实 HTTP 调用（带超时、重试与幂等键）；</li>
 *   <li><b>调用点必须移出 {@code @UseCase} 事务</b>：{@code POST /api/payments} 已经把
 *       {@code create()} 放在接口层、事务外（见 {@code PaymentController#create} 的注释），
 *       退款侧目前仍在 {@code RefundServiceImpl#createCloseRefund} 的事务里，接真通道前必须拆开；</li>
 *   <li>失败要区分「可重试（超时/网络）」与「不可重试（渠道拒付）」，前者不能直接回滚业务状态。</li>
 * </ol>
 */
public class MockPayGateway implements PayGateway {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGN_FIELD = "sign";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";
    /** 模拟渠道交易号前缀：真实通道由渠道生成，这里按流水号可复现地派生。 */
    private static final String CHANNEL_TRADE_PREFIX = "MOCK-";

    private final String secret;
    private final Clock clock;

    public MockPayGateway(String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("签名密钥不能为空");
        }
        this.secret = secret;
        this.clock = clock;
    }

    @Override
    public PayTicket create(String txnNo, String orderNo, long amountCent, String channel) {
        // 这组参数就是「渠道受理回执」：与真实通道一样带渠道交易号与受理时间，
        // 前端（演示环境里的渠道替身）原样回放即可通过 verify。
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnNo", txnNo);
        params.put("orderNo", orderNo);
        params.put("amountCent", String.valueOf(amountCent));
        params.put("channel", channel);
        params.put("payChannelTradeNo", CHANNEL_TRADE_PREFIX + txnNo);
        params.put("paidAtMillis", String.valueOf(clock.millis()));
        return new PayTicket(txnNo, Collections.unmodifiableMap(params), sign(params));
    }

    @Override
    public boolean verify(Map<String, String> callbackParams) {
        if (callbackParams == null || callbackParams.isEmpty()) {
            return false;
        }
        String provided = callbackParams.get(SIGN_FIELD);
        if (provided == null || provided.isBlank()) {
            return false;
        }
        return constantTimeEquals(sign(callbackParams), provided);
    }

    @Override
    public boolean refund(String txnNo, String orderNo, long amountCent) {
        // 模拟通道：仅做「参数合法性」判定，受理即成功；真实通道换成 HTTP 调用 + 验签即可。
        // 拒绝时必须返回 false，由调用方抛 BIZ_REJECT 并回滚关单事务。
        return txnNo != null && !txnNo.isBlank()
                && orderNo != null && !orderNo.isBlank()
                && amountCent > 0;
    }

    /**
     * 生成一次性回调报文（供联调与测试模拟通道通知）。
     * 同一笔交易重复生成结果是稳定的（除 timestamp 外），方便断言与回放。
     */
    public Map<String, String> buildCallback(String txnNo, String orderNo, long amountCent, boolean success) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnNo", txnNo);
        params.put("orderNo", orderNo);
        params.put("amountCent", String.valueOf(amountCent));
        params.put("status", success ? STATUS_PAID : STATUS_FAILED);
        params.put("timestamp", String.valueOf(clock.millis()));
        params.put(SIGN_FIELD, sign(params));
        return params;
    }

    /** 计算签名（剔除 sign 与空值后按键排序）。 */
    public String sign(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((key, value) -> {
            if (!SIGN_FIELD.equals(key) && value != null && !value.isEmpty()) {
                sorted.put(key, value);
            }
        });
        StringBuilder plain = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (!plain.isEmpty()) {
                plain.append('&');
            }
            plain.append(key).append('=').append(value);
        });
        return hmacHex(plain.toString());
    }

    private String hmacHex(String plain) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().withUpperCase().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
