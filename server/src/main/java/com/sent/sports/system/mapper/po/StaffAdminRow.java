package com.sent.sports.system.mapper.po;

/**
 * 员工管理列表行（联查投影）：{@code sys_staff} 左联 {@code sys_role} 取角色编码。
 * <p>只用于管理端读侧分页；写侧仍走 {@link SysStaffPO}，避免把展示用联查字段混进可写 PO。</p>
 */
public class StaffAdminRow {

    private Long staffId;

    private String staffNo;

    private String name;

    private String phone;

    private String roleCode;

    private String status;

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getStaffNo() {
        return staffNo;
    }

    public void setStaffNo(String staffNo) {
        this.staffNo = staffNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
