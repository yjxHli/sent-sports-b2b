package com.sent.sports.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sent.sports.auth.mapper.po.StaffAccountPO;
import org.apache.ibatis.annotations.Mapper;

/** 认证模块自有员工表 Mapper（只认登录名 username，工号 staff_no 仅作展示）。 */
@Mapper
public interface StaffAccountMapper extends BaseMapper<StaffAccountPO> {
}
