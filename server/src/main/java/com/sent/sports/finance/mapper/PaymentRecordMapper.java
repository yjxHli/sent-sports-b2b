package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.mapper.po.PaymentRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 回款单 Mapper（P5-21）：唯一性校验（银行流水号）放在数据库侧，避免应用层「先查再插」的并发漏判。
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecordPO> {

    /** 按回款单号读取（核销与冲销入口）。 */
    @Select("SELECT * FROM payment_record WHERE record_no = #{recordNo}")
    PaymentRecordPO selectByRecordNo(@Param("recordNo") String recordNo);

    /** 银行流水号是否已登记。 */
    @Select("SELECT COUNT(*) FROM payment_record WHERE bank_serial = #{bankSerial} AND bank_serial IS NOT NULL")
    int countBySerialNo(@Param("bankSerial") String bankSerial);

    /**
     * 管理端回款列表（收口阶段）：按客户 / 状态过滤，显式列别名映射到 PaymentRecordPO，
     * 由 MyBatis-Plus {@link Page} 追加 LIMIT/OFFSET，总数另由 {@link #countPaymentRecord} 统计。
     */
    @Select("SELECT id, record_no AS recordNo, customer_id AS customerId, "
            + "amount_cent AS amountCent, written_off_cent AS writtenOffCent, "
            + "received_date AS receivedDate, bank_serial AS bankSerial, remark, status, "
            + "reversed_reason AS reversedReason, operator_id AS operatorId, created_at AS createdAt "
            + "FROM payment_record "
            + "WHERE (#{customerId} IS NULL OR customer_id = #{customerId}) "
            + "AND (#{status} IS NULL OR #{status} = '' OR status = #{status}) "
            + "ORDER BY id DESC")
    IPage<PaymentRecordPO> selectPaymentRecordPage(Page<PaymentRecordPO> page,
                                                   @Param("customerId") Long customerId,
                                                   @Param("status") String status);

    /** 与 selectPaymentRecordPage 同一过滤条件的独立总数。 */
    @Select("SELECT COUNT(*) FROM payment_record "
            + "WHERE (#{customerId} IS NULL OR customer_id = #{customerId}) "
            + "AND (#{status} IS NULL OR #{status} = '' OR status = #{status})")
    long countPaymentRecord(@Param("customerId") Long customerId,
                            @Param("status") String status);
}
