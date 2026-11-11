package com.sent.sports.customize.service;

/**
 * 定制可发货判定门面（跨模块契约）：order 模块发货前经此判定某订单行定制是否完工可发，
 * 不直接依赖 customize 领域类型（ArchUnit 规则 4）。无定制记录的订单行一律视为可发。
 */
public interface CustomizeShipGateway {
    /** 该订单行是否可发货（定制已完工 / 非定制记录）。 */
    boolean isReadyToShip(String orderNo, long skuId);

    /**
     * 为订单中「带定制标记且尚无定制单」的订单行生成 {@code custom_order}。
     * <p>下单时（UV 定制件需立即上传图稿）与发货时（兼容历史订单）都会调用；
     * 由 {@code uk_custom_order_item} + {@code INSERT IGNORE} 保证幂等，重复调用不会重复建单。</p>
     *
     * @return 本次新建的定制单条数；无定制行时返回 0
     */
    int createCustomOrdersForShipment(String orderNo);
}
