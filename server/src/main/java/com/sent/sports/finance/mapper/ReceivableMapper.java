package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.mapper.po.ReceivableListRow;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 应收台账 Mapper（P5-17 + 收口阶段补台账分页）。
 * <p>「一单一应收」由 {@code receivable.order_id} 唯一索引兜底：重复落账走
 * {@code INSERT IGNORE}，返回 0 行即视为已落过账，不引入分布式锁。</p>
 * <p>核销/冲销用条件更新把「不得超额 / 不得减成负数」写成 SQL 谓词，
 * 返回 0 行即业务被拒，跨进程也成立。</p>
 */
@Mapper
public interface ReceivableMapper extends BaseMapper<ReceivablePO> {

    /** 落账（幂等）：order_id 唯一，重复插入被忽略并返回 0。 */
    @Update("INSERT IGNORE INTO receivable (order_id, customer_id, amount_cent, received_cent, due_date, status, "
            + "created_at, updated_at) "
            + "VALUES (#{orderId}, #{customerId}, #{amountCent}, 0, #{dueDate}, #{status}, NOW(3), NOW(3))")
    int insertIgnore(@Param("orderId") long orderId, @Param("customerId") long customerId,
                     @Param("amountCent") long amountCent, @Param("dueDate") LocalDate dueDate,
                     @Param("status") String status);

    /**
     * 幂等落账别名：调用后该订单一定存在应收。
     * <p>返回口径是「落账后的存在性」而非「本次是否插入」——底层 {@code INSERT IGNORE} 在重复时
     * 受影响行数为 0，若直接透传会让调用方误判失败；这里统一返回 1，重复落账仍被数据库忽略。</p>
     */
    default int insertIfAbsent(long orderId, long customerId, long amountCent, LocalDate dueDate, String status) {
        insertIgnore(orderId, customerId, amountCent, dueDate, status);
        return 1;
    }

    /** 订单号 → 订单主键（落账前置查询）。 */
    @Select("SELECT id FROM orders WHERE order_no = #{orderNo}")
    Long selectOrderIdByOrderNo(@Param("orderNo") String orderNo);

    /** 按订单查应收（一单一应收）。 */
    @Select("SELECT * FROM receivable WHERE order_id = #{orderId}")
    ReceivablePO selectByOrderId(@Param("orderId") long orderId);

    /** 已核销累加：写入前用条件保证「累计不得超过应收」。返回 0 = 超额被拒。 */
    @Update("UPDATE receivable SET received_cent = received_cent + #{amount}, updated_at = NOW(3) "
            + "WHERE id = #{id} AND received_cent + #{amount} <= amount_cent")
    int addReceived(@Param("id") long id, @Param("amount") long amount);

    /** 冲销回退已核销：减到负数前拦截。返回 0 = 余额不足被拒。 */
    @Update("UPDATE receivable SET received_cent = received_cent - #{amount}, updated_at = NOW(3) "
            + "WHERE id = #{id} AND received_cent >= #{amount}")
    int subtractReceived(@Param("id") long id, @Param("amount") long amount);

    /** 回写核销进度与状态（用例层算好的终态）。 */
    @Update("UPDATE receivable SET received_cent = #{receivedCent}, status = #{status}, updated_at = NOW(3) "
            + "WHERE id = #{id}")
    int updateProgress(@Param("id") long id, @Param("receivedCent") long receivedCent,
                       @Param("status") String status);

    /** 状态流转（逾期重算等）。 */
    @Update("UPDATE receivable SET status = #{status}, updated_at = NOW(3) WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    /** 未结清应收（核销时按客户取候选）。 */
    @Select("SELECT * FROM receivable WHERE customer_id = #{customerId} AND status <> 'CLOSED' ORDER BY id")
    List<ReceivablePO> selectOpenByCustomer(@Param("customerId") long customerId);

    /** 到期未收：截止日前（不含当天）且未结清。 */
    @Select("SELECT * FROM receivable WHERE status <> 'CLOSED' AND due_date IS NOT NULL "
            + "AND due_date < #{today} ORDER BY due_date, id")
    List<ReceivablePO> selectOverdue(@Param("today") LocalDate today);

    /** 核销并发场景的行锁读取。 */
    @Select("SELECT * FROM receivable WHERE id = #{id} FOR UPDATE")
    ReceivablePO selectByIdForUpdate(@Param("id") long id);

    // ------------------------------------------------------------------
    // 管理端台账分页（订单号联查 orders；已核销取 received_cent）
    // ------------------------------------------------------------------

    @Select("<script>"
            + "SELECT r.id AS receivableId, o.order_no AS orderNo, r.customer_id AS customerId, "
            + "r.amount_cent AS amountCent, r.received_cent AS writtenOffCent, r.due_date AS dueAt, "
            + "r.status AS status "
            + "FROM receivable r "
            + "LEFT JOIN orders o ON o.id = r.order_id "
            + "WHERE (#{customerId} IS NULL OR r.customer_id = #{customerId}) "
            + "AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status}) "
            + "<if test=\"openOnly\"> AND r.status &lt;&gt; 'CLOSED' </if>"
            + "ORDER BY r.id DESC"
            + "</script>")
    IPage<ReceivableListRow> selectReceivablePage(Page<ReceivableListRow> page,
                                                  @Param("customerId") Long customerId,
                                                  @Param("status") String status,
                                                  @Param("openOnly") boolean openOnly);

    @Select("<script>"
            + "SELECT COUNT(*) FROM receivable r "
            + "WHERE (#{customerId} IS NULL OR r.customer_id = #{customerId}) "
            + "AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status}) "
            + "<if test=\"openOnly\"> AND r.status &lt;&gt; 'CLOSED' </if>"
            + "</script>")
    long countReceivable(@Param("customerId") Long customerId, @Param("status") String status,
                         @Param("openOnly") boolean openOnly);
}
