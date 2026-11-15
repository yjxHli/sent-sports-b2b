package com.sent.sports.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.customer.mapper.po.CustomerAddressPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 收货地址 Mapper（{@code customer_address}）。
 *
 * <p>所有按 ID 的读写都**同时**带 {@code customer_id}：地址是个人隐私数据，
 * 只按主键查会让「换个 ID」就能读到别人家地址，越权与不存在一律返回 0 行 / null，
 * 由 service 统一转 404，不用 403 泄露「这条存在」。</p>
 */
@Mapper
public interface CustomerAddressMapper extends BaseMapper<CustomerAddressPO> {

    /** 某位客户的全部地址：默认地址排最前，其次新加的在前。 */
    @Select("SELECT * FROM customer_address WHERE customer_id = #{customerId} "
            + "ORDER BY is_default DESC, id DESC")
    List<CustomerAddressPO> selectByCustomer(@Param("customerId") long customerId);

    /** 按 ID + 归属读取（越权返回 null）。 */
    @Select("SELECT * FROM customer_address WHERE id = #{id} AND customer_id = #{customerId}")
    CustomerAddressPO selectOwned(@Param("id") long id, @Param("customerId") long customerId);

    /** 该客户已有多少条地址（判断「第一条自动默认」）。 */
    @Select("SELECT COUNT(*) FROM customer_address WHERE customer_id = #{customerId}")
    int countByCustomer(@Param("customerId") long customerId);

    /** 清掉该客户现有默认标记：默认地址必须唯一，否则结算页预选结果不确定。 */
    @Update("UPDATE customer_address SET is_default = 0 WHERE customer_id = #{customerId} AND is_default = 1")
    int clearDefault(@Param("customerId") long customerId);

    /** 把某条地址置为默认（带归属条件）。 */
    @Update("UPDATE customer_address SET is_default = 1 WHERE id = #{id} AND customer_id = #{customerId}")
    int setDefault(@Param("id") long id, @Param("customerId") long customerId);

    /** 删除（带归属条件）：返回 0 表示不是自己的或已不存在。 */
    @Delete("DELETE FROM customer_address WHERE id = #{id} AND customer_id = #{customerId}")
    int deleteOwned(@Param("id") long id, @Param("customerId") long customerId);
}
