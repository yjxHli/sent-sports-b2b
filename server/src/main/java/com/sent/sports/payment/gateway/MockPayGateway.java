package com.sent.sports.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
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
 */
public class MockPayGateway implements PayGateway {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGN_FIELD = "sign";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";

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
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("txnNo", txnNo);
        payload.put("orderNo", orderNo);
        payload.put("amountCent", String.valueOf(amountCent));
        payload.put("channel", channel);
        payload.put("timestamp", String.valueOf(clock.millis()));
        String sign = sign(payload);
        return new PayTicket(txnNo, toJson(payload), sign);
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

    private String toJson(Map<String, String> payload) {
        StringBuilder json = new StringBuilder("{");
        payload.forEach((key, value) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(key).append("\":\"").append(value).append('"');
        });
        return json.append('}').toString();
    }
}
