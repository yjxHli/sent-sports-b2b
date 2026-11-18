package com.sent.sports.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.auth.mapper.po.CustomerAccountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 认证模块自有客户表 Mapper（手机号即登录标识，企业审核状态联查档案表）。 */
@Mapper
public interface CustomerAccountMapper extends BaseMapper<CustomerAccountPO> {

    @Select("SELECT c.*, e.status AS enterpriseStatus FROM customer c "
            + "LEFT JOIN enterprise_profile e ON e.customer_id = c.id "
            + "WHERE c.phone = #{phone} AND c.deleted = 0 LIMIT 1")
    CustomerAccountPO selectByPhoneWithEnterprise(@Param("phone") String phone);

    @Select("SELECT c.*, e.status AS enterpriseStatus FROM customer c "
            + "LEFT JOIN enterprise_profile e ON e.customer_id = c.id "
            + "WHERE c.id = #{customerId} AND c.deleted = 0 LIMIT 1")
    CustomerAccountPO selectByIdWithEnterprise(@Param("customerId") long customerId);
}
