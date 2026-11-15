package com.sent.sports.refund.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 退款凭证图片的 JSON 编解码（{@code refund_order.images_json}）。
 *
 * <p>列里存的是 fileId 数组（{@code ["2026/11/ab/xxx.png"]}），文件本体在文件服务里。
 * 读写两侧共用一处实现，避免写进去是 JSON、读出来当纯文本。</p>
 */
public final class ImageJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {
    };

    private ImageJsonCodec() {
    }

    /** 空列表返回 null，落库即 NULL（不写 "[]" 噪声）。 */
    public static String encode(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(images);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析失败/空值一律返回空列表，不让脏数据影响审核页渲染。 */
    public static List<String> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> images = MAPPER.readValue(json, LIST_OF_STRING);
            return images == null ? List.of() : images.stream().filter(s -> s != null && !s.isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
