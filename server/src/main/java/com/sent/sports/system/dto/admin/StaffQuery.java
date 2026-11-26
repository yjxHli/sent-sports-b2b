package com.sent.sports.system.dto.admin;

import com.sent.sports.common.web.PageQuery;

/**
 * 员工管理列表查询条件：继承 {@code page / size / sort}，附加关键词与状态筛选。
 * <p>关键词一次输入覆盖员工号（staff_no）、登录名（username）、姓名、手机号；
 * 状态与列表列的 {@code enabled} 派生口径一致：只有 ACTIVE 显示启用中，其余显示已停用。</p>
 */
public class StaffQuery extends PageQuery {

    private String keyword;

    private Boolean enabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /** 归一后的关键词（空白 → null），避免空条件污染 LIKE。 */
    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    /** 状态无需二次归一：null 表示不限，其余交给 MyBatis 做 ACTIVE / 非 ACTIVE 判断。 */
    public Boolean normalizedEnabled() {
        return enabled;
    }
}
