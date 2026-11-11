package com.sent.sports.system.dto.admin;

import com.sent.sports.common.web.PageQuery;

/**
 * 操作日志查询条件：继承 {@code page / size / sort}，附加服务端筛选字段。
 * <p>所有条件最终以 {@code #{...}} 参数化进入 SQL，禁止字符串拼接；
 * 空白字符串统一归一为 {@code null}，避免「空条件恒真」把整表日志返回。</p>
 */
public class LogQuery extends PageQuery {

    private String module;

    private String action;

    private Long staffId;

    private String targetType;

    private String targetId;

    private String keyword;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    /** 归一后的模块筛选（空白 → null）。 */
    public String normalizedModule() {
        return blankToNull(module);
    }

    /** 归一后的动作筛选（空白 → null）。 */
    public String normalizedAction() {
        return blankToNull(action);
    }

    /** 归一后的对象类型筛选（空白 → null）。 */
    public String normalizedTargetType() {
        return blankToNull(targetType);
    }

    /** 归一后的对象 ID 筛选（空白 → null）。 */
    public String normalizedTargetId() {
        return blankToNull(targetId);
    }

    /** 归一后的关键字（空白 → null）。 */
    public String normalizedKeyword() {
        return blankToNull(keyword);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
