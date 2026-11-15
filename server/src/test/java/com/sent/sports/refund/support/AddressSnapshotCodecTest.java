package com.sent.sports.refund.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 订单收货地址快照解码测试（TDD 红→绿）。
 *
 * <p>{@code orders.address_snapshot_json} 存的是下单时的收货信息原文，
 * 退款审核页要显示「这批货发给了谁、地址在哪」，因此需要一个容错的只读解码器：
 * 脏数据/历史空值只能让对应字段为空，绝不能让整页 500。</p>
 */
class AddressSnapshotCodecTest {

    @Test
    @DisplayName("完整快照：省市区与详细地址拼成一行收货地址")
    void decodesFullSnapshot() {
        String json = "{\"receiver\":\"张三\",\"phone\":\"13800138000\",\"province\":\"广东省\","
                + "\"city\":\"深圳市\",\"district\":\"南山区\",\"detail\":\"科技园路 5 号\"}";

        AddressSnapshotCodec.Receiver r = AddressSnapshotCodec.decode(json);

        assertEquals("张三", r.receiver());
        assertEquals("13800138000", r.phone());
        assertEquals("广东省 深圳市 南山区 科技园路 5 号", r.address());
    }

    @Test
    @DisplayName("省份缺失时不产生多余空格（手填地址常见）")
    void skipsMissingSegments() {
        String json = "{\"receiver\":\"李四\",\"phone\":\"13900139000\",\"city\":\"深圳市\","
                + "\"detail\":\"前海路 1 号\"}";

        AddressSnapshotCodec.Receiver r = AddressSnapshotCodec.decode(json);

        assertEquals("深圳市 前海路 1 号", r.address());
    }

    @Test
    @DisplayName("空 JSON / null：返回全空快照而不是抛异常")
    void emptyInputGivesEmptySnapshot() {
        AddressSnapshotCodec.Receiver r = AddressSnapshotCodec.decode(null);

        assertNull(r.receiver());
        assertNull(r.phone());
        assertEquals("", r.address());
        assertEquals("", AddressSnapshotCodec.decode("   ").address());
    }

    @Test
    @DisplayName("脏数据（非 JSON / JSON 数组）：降级为空快照，不影响审核页渲染")
    void brokenJsonDegradesToEmpty() {
        assertEquals("", AddressSnapshotCodec.decode("not-a-json").address());
        assertEquals("", AddressSnapshotCodec.decode("[1,2,3]").address());
        assertNull(AddressSnapshotCodec.decode("not-a-json").receiver());
    }

    @Test
    @DisplayName("数字型字段按文本读出，不因类型不符整条丢弃")
    void numericFieldReadAsText() {
        AddressSnapshotCodec.Receiver r = AddressSnapshotCodec.decode("{\"receiver\":123,\"phone\":13800138000}");

        assertEquals("123", r.receiver());
        assertEquals("13800138000", r.phone());
    }
}
