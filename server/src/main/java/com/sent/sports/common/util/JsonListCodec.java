package com.sent.sports.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON 数组编解码（V54）：商品图集 / 详情图 / 参数 / 服务承诺四类"字符串数组型"字段共用。
 *
 * <p><b>为什么空值也写 {@code "[]"}</b>：MyBatis-Plus {@code updateById} 会跳过 null 字段，
 * 若清空时把列置 null，删除操作会静默失效（旧图仍在）。这里统一落 {@code "[]"}，
 * 语义明确且能被 SQL 覆盖。</p>
 *
 * <p><b>为什么读失败不抛异常</b>：库里可能存着历史脏 JSON，详情页不能因此 500。
 * 解析失败降级为空列表，页面照常打开（只是少了那个区块）。</p>
 *
 * <p>注：只处理 String / 简单 record 列表，不涉及项目定制的 Long→String 序列化策略，
 * 因此用独立 ObjectMapper，不注入全局实例。</p>
 */
public final class JsonListCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonListCodec() {
    }

    /** 序列化；null 或空列表一律返回 {@code "[]"}（表示清空，覆盖旧值）。 */
    public static <T> String write(List<T> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }

    /** 反序列化；null / 空白 / 脏 JSON 一律返回空列表，绝不抛异常。 */
    public static <T> List<T> read(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            return List.of();
        }
    }
}
