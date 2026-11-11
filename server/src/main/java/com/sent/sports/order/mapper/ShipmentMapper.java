package com.sent.sports.order.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 包裹 Mapper（验收缺口补齐）：一次发货 = 一条 {@code shipment} + 若干 {@code shipment_item}。
 *
 * <p>包裹明细用 {@code INSERT ... SELECT} 从 {@code order_item} 按 sku 反查行 ID，
 * 避免在用例层把持久化主键泄漏到领域对象里；{@code uk_shipment_no} 保证包裹号唯一。</p>
 */
@Mapper
public interface ShipmentMapper {

    /** 由订单号取订单主键（写包裹外键用）。 */
    @Select("SELECT id FROM orders WHERE order_no = #{orderNo}")
    Long selectOrderIdByOrderNo(@Param("orderNo") String orderNo);

    /** 写入包裹主记录，shipped_at 由数据库时钟统一生成。 */
    @Insert("INSERT INTO shipment (shipment_no, order_id, carrier, tracking_no, shipped_at, status) "
            + "VALUES (#{shipmentNo}, #{orderId}, #{carrier}, #{trackingNo}, NOW(3), 'SHIPPED')")
    int insertShipment(@Param("shipmentNo") String shipmentNo,
                       @Param("orderId") long orderId,
                       @Param("carrier") String carrier,
                       @Param("trackingNo") String trackingNo);

    /** 回查刚写入的包裹主键（不依赖 JDBC getGeneratedKeys，跨连接池行为一致）。 */
    @Select("SELECT id FROM shipment WHERE shipment_no = #{shipmentNo}")
    Long selectIdByShipmentNo(@Param("shipmentNo") String shipmentNo);

    /** 按订单行写入本次包裹明细。 */
    @Insert("INSERT INTO shipment_item (shipment_id, order_item_id, qty) "
            + "SELECT #{shipmentId}, oi.id, #{qty} FROM order_item oi "
            + "WHERE oi.order_id = #{orderId} AND oi.sku_id = #{skuId}")
    int insertItemForSku(@Param("shipmentId") long shipmentId,
                         @Param("orderId") long orderId,
                         @Param("skuId") long skuId,
                         @Param("qty") int qty);
}
