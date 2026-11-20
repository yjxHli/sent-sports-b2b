package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.finance.dto.admin.AdminFinanceDtos;
import com.sent.sports.finance.mapper.po.WriteOffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 核销明细 Mapper（P5-21）：按回款单号反查全部明细行，由仓储层按 {@code write_off_no} 分组还原聚合。
 */
@Mapper
public interface WriteOffMapper extends BaseMapper<WriteOffPO> {

    /** 某回款单的全部核销明细（冲销时整体解绑）。 */
    @Select("SELECT * FROM write_off WHERE deleted = 0 AND payment_record_no = #{paymentRecordNo} "
            + "ORDER BY write_off_no, id")
    List<WriteOffPO> selectByPaymentNo(@Param("paymentRecordNo") String paymentRecordNo);

    /** 管理端批量软删核销/冲销明细：不回滚回款与应收金额。 */
    @Update({"<script>", "UPDATE write_off SET deleted = 1 WHERE deleted = 0 AND id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    int batchMarkDeleted(@Param("ids") List<Long> ids);

    /** 核销/冲销明细分页：可按客户收敛，列表与 count 同条件。 */
    @Select("SELECT w.id AS id, w.write_off_no AS writeOffNo, "
            + "w.payment_record_no AS paymentRecordNo, w.customer_id AS customerId, "
            + "w.receivable_id AS receivableId, w.amount_cent AS amountCent, "
            + "UNIX_TIMESTAMP(w.created_at) * 1000 AS createdAtMillis "
            + "FROM write_off w WHERE w.deleted = 0 "
            + "AND (#{customerId} IS NULL OR w.customer_id = #{customerId}) "
            + "ORDER BY w.id DESC")
    IPage<AdminFinanceDtos.WriteOffRow> selectWriteOffPage(Page<AdminFinanceDtos.WriteOffRow> page,
                                                           @Param("customerId") Long customerId);

    @Select("SELECT COUNT(*) FROM write_off w WHERE w.deleted = 0 "
            + "AND (#{customerId} IS NULL OR w.customer_id = #{customerId})")
    long countWriteOff(@Param("customerId") Long customerId);
}
