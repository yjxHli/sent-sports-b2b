package com.sent.sports.refund.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退款凭证图片编解码测试：{@code refund_order.images_json} 里存 fileId 数组，
 * 写读两侧必须共用一处实现，否则会出现「写进去是 JSON、读出来当纯文本」。
 */
class ImageJsonCodecTest {

    @Test
    @DisplayName("编码：fileId 数组写成 JSON 数组字符串")
    void encodeWritesJsonArray() {
        assertEquals("[\"2026/11/ab/a.png\",\"2026/11/ab/b.jpg\"]",
                ImageJsonCodec.encode(List.of("2026/11/ab/a.png", "2026/11/ab/b.jpg")));
    }

    @Test
    @DisplayName("编码：空列表与 null 都落 NULL（不写 \"[]\" 噪声）")
    void encodeBlankReturnsNull() {
        assertNull(ImageJsonCodec.encode(null));
        assertNull(ImageJsonCodec.encode(List.of()));
    }

    @Test
    @DisplayName("解码：与编码互逆")
    void decodeRoundTrip() {
        String json = ImageJsonCodec.encode(List.of("x/1.png", "x/2.png"));
        assertEquals(List.of("x/1.png", "x/2.png"), ImageJsonCodec.decode(json));
    }

    @Test
    @DisplayName("解码：NULL / 空白返回空列表，不让空值影响审核页渲染")
    void decodeBlankReturnsEmpty() {
        assertTrue(ImageJsonCodec.decode(null).isEmpty());
        assertTrue(ImageJsonCodec.decode("   ").isEmpty());
    }

    @Test
    @DisplayName("解码：脏数据（非 JSON / 非字符串数组）返回空列表而不抛异常")
    void decodeCorruptedReturnsEmpty() {
        assertTrue(ImageJsonCodec.decode("not-json").isEmpty());
        assertTrue(ImageJsonCodec.decode("{\"a\":1}").isEmpty());
    }

    @Test
    @DisplayName("解码：过滤数组里的空白项与 null 项")
    void decodeFiltersBlankEntries() {
        assertEquals(List.of("keep.png"), ImageJsonCodec.decode("[\"keep.png\",\"\",null,\"  \"]"));
    }
}
