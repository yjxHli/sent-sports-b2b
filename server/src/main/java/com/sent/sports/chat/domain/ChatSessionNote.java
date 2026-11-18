package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 会话小结（2026-11-18）：客服完结时填写的情况记录。
 * <p>纯 POJO；所有必填与枚举口径在此校验，保证 REST 与未来其它入口同一套规则。</p>
 */
public class ChatSessionNote {

    public enum Category {
        QUALITY, INQUIRY, UNCLEAR
    }

    public enum Info {
        CLEAR, NO_REPLY
    }

    public enum Result {
        DONE, UNRESOLVED
    }

    private final long sessionId;
    private final long customerId;
    private final long staffId;
    private final String summary;
    private final Category category;
    private final Info info;
    private final Result result;
    private final String solution;

    public ChatSessionNote(long sessionId, long customerId, long staffId, String summary,
                           String category, String info, String result, String solution) {
        this.sessionId = sessionId;
        this.customerId = customerId;
        this.staffId = staffId;
        this.summary = require(summary, "问题概述");
        this.category = parse(Category.class, category, "问题分类");
        this.info = parse(Info.class, info, "用户问题信息");
        this.result = parse(Result.class, result, "处理结果");
        this.solution = require(solution, "解决办法");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "不能为空");
        }
        return value.trim();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "取值不合法");
        }
    }

    public long getSessionId() { return sessionId; }
    public long getCustomerId() { return customerId; }
    public long getStaffId() { return staffId; }
    public String getSummary() { return summary; }
    public Category getCategory() { return category; }
    public Info getInfo() { return info; }
    public Result getResult() { return result; }
    public String getSolution() { return solution; }
}
