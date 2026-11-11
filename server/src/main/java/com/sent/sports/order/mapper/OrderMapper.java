package com.sent.sports.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.anno.DataScope;
import com.sent.sports.order.mapper.po.OrderDetailLineRow;
import com.sent.sports.order.mapper.po.OrderItemPO;
import com.sent.sports.order.mapper.po.OrderPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper（P5-13）：订单头与订单行同属一个聚合，故收敛在同一 Mapper，避免拆散后事务边界外泄。
 * <p>两条关键约定：
 * <ul>
 *   <li><b>幂等</b>：{@code idem_key} 有唯一索引，重复提交由 {@code DuplicateKeyException} 暴露，调用方据此回查已有订单返回；</li>
 *   <li><b>状态迁移</b>：一律「条件更新」（{@code WHERE status = 期望旧值}），返回 0 行即说明并发下已被他人推进，
 *       调用方抛 {@code STATE_CONFLICT(40901)}；这样状态机不会被并发送货/取消打穿。</li>
 * </ul>
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {

    /**
     * 管理端订单列表（P5-28 数据范围样板）：列表与导出都走本方法，
     * {@link DataScope} 由 MyBatis 拦截器按角色追加 {@code ALL/TEAM/SELF} 条件，避免「列表看不到但导出能拿到」。
     */
    @DataScope(alias = "o", column = "owner_staff_id")
    @Select("SELECT o.* FROM orders o WHERE o.status = #{status} ORDER BY o.id DESC LIMIT #{limit}")
    List<OrderPO> selectForAdminList(@Param("status") String status, @Param("limit") int limit);

    /** 我的订单真实分页（任务 2：替换 P4 空桩）。 */
    @Select("SELECT * FROM orders WHERE customer_id = #{customerId} ORDER BY id DESC")
    IPage<OrderPO> selectMyPage(Page<OrderPO> page, @Param("customerId") long customerId);

    /**
     * 管理端订单真实分页：status 为空时全量，非空时按状态筛选。
     * <p>发货工作台只应看到 WAIT_SHIP / PART_SHIPPED：此前固定全表查询把 1416 笔
     * COMPLETED 混进第一页，选中后发货必然命中 ShipOrderUseCase 的 409。</p>
     */
    @Select("SELECT * FROM orders "
            + "WHERE (#{status} IS NULL OR #{status} = '' OR status = #{status}) "
            + "ORDER BY id DESC")
    IPage<OrderPO> selectAllPage(Page<OrderPO> page, @Param("status") String status);

    /** 按订单号读取（业务唯一键，前台入口）。 */
    @Select("SELECT * FROM orders WHERE order_no = #{orderNo}")
    OrderPO selectByOrderNo(@Param("orderNo") String orderNo);

    /** 按幂等键回查（重复提交时返回首次结果）。 */
    @Select("SELECT * FROM orders WHERE idem_key = #{idemKey}")
    OrderPO selectByIdemKey(@Param("idemKey") String idemKey);

    /** 订单是否已存在（仓储据此区分「新增」与「更新」）。 */
    @Select("SELECT COUNT(*) FROM orders WHERE order_no = #{orderNo}")
    int existsByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 更新订单头（金额/状态/版本），带乐观锁：{@code version} 不匹配返回 0 行，
     * 调用方抛 {@code STATE_CONFLICT(40901)}，避免并发送货/取消互相覆盖。
     */
    @Update("UPDATE orders SET status = #{status}, pay_status = #{payStatus}, paid_cent = #{paidCent}, "
            + "received_cent = #{receivedCent}, "
            + "paid_at = COALESCE(#{paidAt,jdbcType=TIMESTAMP}, paid_at), version = version + 1 "
            + "WHERE order_no = #{orderNo} AND version = #{version}")
    int updateHeader(OrderPO order);

    /** 同步订单行已发数量（发货后回写）。 */
    @Update("UPDATE order_item SET shipped_qty = #{shippedQty} WHERE order_id = #{orderId} AND sku_id = #{skuId}")
    int updateItemShipped(@Param("orderId") long orderId,
                          @Param("skuId") long skuId,
                          @Param("shippedQty") int shippedQty);

    /** 主状态条件迁移：返回 0 表示旧状态不匹配（并发冲突）。 */
    @Update("UPDATE orders SET status = #{to}, version = version + 1 "
            + "WHERE order_no = #{orderNo} AND status = #{from}")
    int transitStatus(@Param("orderNo") String orderNo,
                      @Param("from") String from,
                      @Param("to") String to);

    /**
     * 关单条件迁移：一次性推进主状态与支付维度，并带上「旧状态 + 旧支付状态」双条件。
     * <p>返回 0 行表示并发下已被他人推进/重复关单，调用方抛 STATE_CONFLICT；
     * 这样重复调用不会二次退款、二次释放库存。</p>
     */
    @Update("UPDATE orders SET status = 'CLOSED', pay_status = #{payStatus}, version = version + 1 "
            + "WHERE order_no = #{orderNo} AND status = #{fromStatus} AND pay_status = #{fromPayStatus}")
    int closeOnAdminRefund(@Param("orderNo") String orderNo,
                           @Param("fromStatus") String fromStatus,
                           @Param("payStatus") String payStatus,
                           @Param("fromPayStatus") String fromPayStatus);

    /**
     * 支付回调落账：仅当尚未支付时生效，天然幂等（迟到/重复回调不会二次加钱）。
     *
     * @return 1 表示本次回调完成落账；0 表示已支付过，属重复回调
     */
    @Update("UPDATE orders SET pay_status = 'PAID', status = 'WAIT_SHIP', "
            + "paid_cent = #{paidCent}, paid_at = NOW(3), version = version + 1 "
            + "WHERE order_no = #{orderNo} AND pay_status <> 'PAID' AND status <> 'CANCELLED'")
    int markPaid(@Param("orderNo") String orderNo, @Param("paidCent") long paidCent);

    /** 超时未支付订单（P5-39 取消任务取数；带过期时间索引）。 */
    @Select("SELECT * FROM orders WHERE status = 'WAIT_PAY' AND expire_at IS NOT NULL "
            + "AND expire_at <= #{now} ORDER BY expire_at LIMIT #{limit}")
    List<OrderPO> selectTimeoutUnpaid(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 发货后长期未确认收货的订单（P5-40 自动确认，按最后更新时间判定）。 */
    @Select("SELECT * FROM orders WHERE status = 'WAIT_RECEIVE' AND updated_at <= #{before} "
            + "ORDER BY updated_at LIMIT #{limit}")
    List<OrderPO> selectAwaitingReceiveBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /** 某订单的全部订单行（快照）。 */
    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItemPO> selectItems(@Param("orderId") long orderId);

    /**
     * 管理端订单详情行（一次联查取全）：订单行快照 + 定制单状态。
     * <p>{@code custom_order.order_item_id} 上有唯一索引，左联不会产生重复行；
     * 定制行但定制单尚未落库时显示「待提交图稿」，普通行显示 NONE。</p>
     * <p>历史订单行的 {@code spu_title_snap/spec_snap} 可能为空（下单聚合只带 SKU 与价格），
     * 因此读侧用 {@code COALESCE} 回退到 sku/product 的当前值，保证详情页不出现空白商品列；
     * 快照存在时仍以快照为准，不会被后续改名覆盖。</p>
     */
    @Select("SELECT oi.sku_id AS skuId, "
            + "COALESCE(oi.spu_title_snap, p.title) AS skuName, "
            + "COALESCE(oi.spec_snap, JSON_UNQUOTE(JSON_EXTRACT(sk.spec_json, '$.spec')), sk.sku_no) AS spec, "
            + "oi.qty AS qty, oi.unit_price_cent AS unitPriceCent, oi.shipped_qty AS shippedQty, "
            + "COALESCE(co.status, CASE WHEN oi.custom_flag = 1 THEN 'WAIT_DRAFT' ELSE 'NONE' END) AS customStatus "
            + "FROM order_item oi "
            + "LEFT JOIN sku sk ON sk.id = oi.sku_id "
            + "LEFT JOIN product p ON p.id = sk.spu_id "
            + "LEFT JOIN custom_order co ON co.order_item_id = oi.id "
            + "WHERE oi.order_id = #{orderId} ORDER BY oi.id")
    List<OrderDetailLineRow> selectDetailLines(@Param("orderId") long orderId);

    /** 写入订单行。 */
    @Insert("INSERT INTO order_item (order_id, sku_id, spu_title_snap, spec_snap, qty, unit_price_cent, "
            + "price_source, price_rule_id, amount_cent, discount_alloc_cent, shipped_qty, custom_flag, custom_fee_cent) "
            + "VALUES (#{orderId}, #{skuId}, #{spuTitleSnap}, #{specSnap}, #{qty}, #{unitPriceCent}, "
            + "#{priceSource}, #{priceRuleId}, #{amountCent}, #{discountAllocCent}, #{shippedQty}, #{customFlag}, #{customFeeCent})")
    int insertItem(OrderItemPO item);

    /** 累加已发数量：防超发（已发 + 本次 ≤ 订单量）。 */
    @Update("UPDATE order_item SET shipped_qty = shipped_qty + #{qty} "
            + "WHERE id = #{itemId} AND shipped_qty + #{qty} <= qty")
    int increaseShippedQty(@Param("itemId") long itemId, @Param("qty") int qty);

    /** 记录状态迁移流水（审计与客服追溯）。 */
    @Insert("INSERT INTO order_status_log (order_id, from_status, to_status, dimension, operator_id, remark) "
            + "VALUES (#{orderId}, #{from}, #{to}, #{dimension}, #{operatorId}, #{remark})")
    int insertStatusLog(@Param("orderId") long orderId,
                        @Param("from") String from,
                        @Param("to") String to,
                        @Param("dimension") String dimension,
                        @Param("operatorId") Long operatorId,
                        @Param("remark") String remark);
}
