package com.sent.sports.customize.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 定制订单四类筛选桶（2026-11-28 用户要求：列表页四个统计卡要能点着筛）。
 *
 * <p>为什么不让前端自己按状态过滤：列表是服务端分页的，前端过滤只会筛掉当前页，
 * 「共 N 单」与实际能翻到的条数对不上。所以桶的口径必须落到 SQL 的 {@code WHERE status IN (...)}，
 * 且统计与列表共用同一份定义（写两遍迟早会飘）。</p>
 *
 * <p>{@code CANCELLED}（已取消）不属于任何桶，只在「全部」里出现——它既不是待办、
 * 也谈不上制作中或已完成，硬塞进任何一桶都会让数字变得没法解释。</p>
 */
public enum CustomStatusBucket {

    /** 待我处理：待提交图稿 / 审核驳回（要客户本人再传一版） */
    TODO("待我处理", CustomStatus.WAIT_DRAFT, CustomStatus.REJECTED),

    /** 审核中：待审核 / 待主管处理（都还没出审图结果） */
    REVIEW("审核中", CustomStatus.WAIT_REVIEW, CustomStatus.ESCALATED),

    /** 制作中：审核通过 / 生产中 */
    PRODUCING("制作中", CustomStatus.APPROVED, CustomStatus.IN_PRODUCTION),

    /** 已完成：已完工 / 已发货 */
    DONE("已完成", CustomStatus.FINISHED, CustomStatus.SHIPPED);

    private final String label;
    private final List<CustomStatus> statuses;

    CustomStatusBucket(String label, CustomStatus... statuses) {
        this.label = label;
        this.statuses = List.of(statuses);
    }

    public String getLabel() {
        return label;
    }

    public List<CustomStatus> getStatuses() {
        return statuses;
    }

    /** 供 Mapper 的 {@code status IN (...)} 使用：状态名与库里存的值一一对应。 */
    public List<String> statusNames() {
        return statuses.stream().map(Enum::name).toList();
    }

    public boolean matches(String status) {
        return status != null && statuses.stream().anyMatch(s -> s.name().equals(status));
    }

    /**
     * 前端传 {@code bucket=todo|review|producing|done}（大小写不敏感）。
     * <p>空白返回 null = 不筛；非法值也返回 null 由调用方决定怎么处理（当前口径是忽略，不 400——
     * 分享出去的旧链接多了个参数不该把页面打挂）。</p>
     */
    public static CustomStatusBucket of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(b -> b.name().equals(key))
                .findFirst()
                .orElse(null);
    }
}
