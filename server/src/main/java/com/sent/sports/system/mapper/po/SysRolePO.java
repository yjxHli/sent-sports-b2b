package com.sent.sports.system.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 角色表 {@code sys_role} 持久化对象（P5-10）。
 * <p>{@code dataScope} 取 ALL / TEAM / SELF，由 {@code DataScopeInterceptor}（P5-28）据此追加数据范围条件。
 */
@TableName("sys_role")
public class SysRolePO extends BasePO {

    private String code;
    private String name;
    private String dataScope;
    private Integer isBuiltin;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public Integer getIsBuiltin() {
        return isBuiltin;
    }

    public void setIsBuiltin(Integer isBuiltin) {
        this.isBuiltin = isBuiltin;
    }
}
