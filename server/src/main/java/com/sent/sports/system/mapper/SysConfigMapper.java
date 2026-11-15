package com.sent.sports.system.mapper;

import com.sent.sports.system.mapper.po.ParamRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 业务参数 Mapper（{@code sys_config}）。
 * <p>参数是「键 → 字符串值」的扁平配置，数量小、整表读取即可；
 * 写入用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 一次完成新增/更新，依赖 {@code uk_config_key}
 * 保证幂等（与项目「防重只用 DB 唯一索引」的约定一致，不引入分布式锁）。</p>
 */
@Mapper
public interface SysConfigMapper {

    /** 全部参数（按键名升序，便于运营定位）。 */
    @Select("SELECT config_key AS paramKey, config_value AS paramValue, remark AS remark "
            + "FROM sys_config ORDER BY config_key")
    List<ParamRow> selectAll();

    /** 新增或更新一个参数；已存在时只改值，不改备注。 */
    @Insert("INSERT INTO sys_config (config_key, config_value, created_at, updated_at) "
            + "VALUES (#{paramKey}, #{paramValue}, NOW(3), NOW(3)) "
            + "ON DUPLICATE KEY UPDATE config_value = #{paramValue}, updated_at = NOW(3)")
    int upsert(@Param("paramKey") String paramKey, @Param("paramValue") String paramValue);
}
