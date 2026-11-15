package com.sent.sports.refund.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单收货地址快照解码（{@code orders.address_snapshot_json}）。
 *
 * <p>退款审核要看「这批货发给了谁、发到哪儿」，但快照是下单时的自由 JSON：
 * 历史数据可能缺省市区、可能是脏串、也可能整列为空。这里的取数策略是
 * **能读多少读多少**——单个字段类型不对只丢该字段，绝不让审核页整页 500。</p>
 */
public final class AddressSnapshotCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AddressSnapshotCodec() {
    }

    /** 收货信息快照：收货人、电话、拼成一行的详细地址（缺省段自动跳过，不产生多余空格）。 */
    public record Receiver(String receiver, String phone, String address) {

        /** 空快照（无快照 / 解析失败）：字段全空，前端显示「--」。 */
        public static final Receiver EMPTY = new Receiver(null, null, "");
    }

    public static Receiver decode(String json) {
        if (json == null || json.isBlank()) {
            return Receiver.EMPTY;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            return Receiver.EMPTY;
        }
        if (node == null || !node.isObject()) {
            return Receiver.EMPTY;
        }
        List<String> segments = new ArrayList<>(4);
        for (String key : List.of("province", "city", "district", "detail")) {
            String value = text(node, key);
            if (value != null) {
                segments.add(value);
            }
        }
        return new Receiver(text(node, "receiver"), text(node, "phone"), String.join(" ", segments));
    }

    /** 取文本：缺失/空串归一为 null，数字与布尔按原样转文本（老数据里电话可能是数字）。 */
    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }
}
