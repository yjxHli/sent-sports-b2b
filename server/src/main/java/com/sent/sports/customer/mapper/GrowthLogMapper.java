package com.sent.sports.customer.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 成长值流水 Mapper（验收缺口补齐）。
 * <p>幂等完全交给 V12 新增的 {@code uk_growth_source(customer_id, source_type, source_id)}：
 * 同一订单重复触发确认收货时 {@code INSERT IGNORE} 返回 0，调用方据此跳过余额累加，
 * 不引入分布式锁，也不做「先查后写」的竞态判断。</p>
 */
@Mapper
public interface GrowthLogMapper {

    /** @return 1 表示本次首次落账；0 表示该来源已记过流水（重放） */
    @Insert("INSERT IGNORE INTO growth_log "
            + "(customer_id, change_value, balance_after, source_type, source_id) "
            + "VALUES (#{customerId}, #{delta}, #{balanceAfter}, #{sourceType}, #{sourceId})")
    int insertIfAbsent(@Param("customerId") long customerId,
                       @Param("delta") long delta,
                       @Param("balanceAfter") long balanceAfter,
                       @Param("sourceType") String sourceType,
                       @Param("sourceId") String sourceId);
}
