package com.sent.sports.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.customer.mapper.po.TransferLogPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户改派日志 Mapper（{@code customer_owner_log}，2026-11-28 起由真库仓储使用）。
 *
 * <p>按客户查历史用自增 id 倒序（id 递增 = 时间递增，与项目里评价列表的口径一致），
 * 不依赖 {@code created_at} 排序——同毫秒写入多条时时间列分不出先后。
 */
@Mapper
public interface TransferLogMapper extends BaseMapper<TransferLogPO> {

    @Select("SELECT id, customer_id, from_staff_id, to_staff_id, reason, operator_id, "
            + "created_at, updated_at, created_by "
            + "FROM customer_owner_log WHERE customer_id = #{customerId} ORDER BY id DESC")
    List<TransferLogPO> selectByCustomerId(@Param("customerId") long customerId);
}
