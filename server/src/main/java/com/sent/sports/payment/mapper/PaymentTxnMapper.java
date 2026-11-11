package com.sent.sports.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 支付流水 Mapper（P5-14）。
 * <p>唯一键冲突的处理权留给调用方：{@code insert} 抛 {@code DuplicateKeyException} 时，
 * 仓储实现（P5-19 之后）回查已有流水并返回，从而实现「客户端重试不重复下单/不重复扣款」。
 */
@Mapper
public interface PaymentTxnMapper extends BaseMapper<PaymentTxnPO> {

    /** 按通道流水号读取（回调去重的第一入口）。 */
    @Select("SELECT * FROM payment_txn WHERE txn_no = #{txnNo}")
    PaymentTxnPO selectByTxnNo(@Param("txnNo") String txnNo);

    /** 按幂等键读取（前台重复发起支付时回查首次流水）。 */
    @Select("SELECT * FROM payment_txn WHERE idem_key = #{idemKey}")
    PaymentTxnPO selectByIdemKey(@Param("idemKey") String idemKey);

    /** 某订单最近一笔流水（订单详情展示支付结果）。 */
    @Select("SELECT * FROM payment_txn WHERE order_no = #{orderNo} ORDER BY id DESC LIMIT 1")
    PaymentTxnPO selectLatestByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 回调落账：仅 CREATED 状态可转 PAID，迟到/重复回调返回 0 行，天然幂等。
     */
    @Update("UPDATE payment_txn SET status = 'PAID', channel_trade_no = #{tradeNo}, "
            + "callback_at = NOW(3), raw_payload = #{payload} WHERE txn_no = #{txnNo} AND status = 'CREATED'")
    int markCallbackPaid(@Param("txnNo") String txnNo,
                         @Param("tradeNo") String tradeNo,
                         @Param("payload") String payload);

    /** 支付失败 / 关闭：仅 CREATED 可改，避免把已支付流水改坏。 */
    @Update("UPDATE payment_txn SET status = #{status}, raw_payload = #{payload} "
            + "WHERE txn_no = #{txnNo} AND status = 'CREATED'")
    int closeOnFailure(@Param("txnNo") String txnNo,
                       @Param("status") String status,
                       @Param("payload") String payload);

    /**
     * 全额退款：只有已支付流水可转 REFUNDED；条件更新天然幂等，
     * 重复退款（或状态已被其他流程推进）返回 0 行，不会把状态改坏。
     */
    @Update("UPDATE payment_txn SET status = 'REFUNDED', callback_at = NOW(3) "
            + "WHERE txn_no = #{txnNo} AND status = 'PAID'")
    int markRefunded(@Param("txnNo") String txnNo);
}
