package com.sent.sports.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.finance.mapper.po.WriteOffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 核销明细 Mapper（P5-21）：按回款单号反查全部明细行，由仓储层按 {@code write_off_no} 分组还原聚合。
 */
@Mapper
public interface WriteOffMapper extends BaseMapper<WriteOffPO> {

    /** 某回款单的全部核销明细（冲销时整体解绑）。 */
    @Select("SELECT * FROM write_off WHERE payment_record_no = #{paymentRecordNo} ORDER BY write_off_no, id")
    List<WriteOffPO> selectByPaymentNo(@Param("paymentRecordNo") String paymentRecordNo);
}
