package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.time.LocalDateTime;

/**
 * 服务工单记录（原会话小结，2026-11-18 → 2026-11-25 升级工单字段 → 2026-11-26 增加暂存/提交）。
 * <p>纯 POJO；所有必填与枚举口径在此校验，保证 REST 与未来其它入口同一套规则。
 * 必填项缺失统一抛 {@link ErrorCode#SERVICE_TICKET_INCOMPLETE}（40005），
 * 而不是普通 40001——前端据此提示「工单未填写完整」。</p>
 *
 * <p><b>暂存与提交的区别（2026-11-26 用户要求）</b>：客服可以先「暂时保存」离开去接待别的客户，
 * 空闲时回来继续填，因此草稿允许必填项为空（{@link #draft}）；只有「提交」才校验完整，
 * 也只有 {@link SubmitStatus#SUBMITTED} 的工单会出现在管理端客服审查里。</p>
 */
public class ChatSessionNote {

    /** 工单状态：DRAFT 暂存（可缺项）/ SUBMITTED 已提交（进审查）。 */
    public enum SubmitStatus {
        DRAFT, SUBMITTED
    }

    /** 问题分类：兼容历史英文码与前端中文文案。 */
    public enum Category {
        QUALITY("商品品质"), INQUIRY("商品咨询"), AFTER_SALE("订单售后"),
        LOGISTICS("物流配送"), UNCLEAR("未明确"), OTHER("其他");

        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Info {
        CLEAR("明确信息"), NO_REPLY("客户未回复");

        private final String label;
        Info(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Result {
        DONE("已完成"), UNRESOLVED("未解决");

        private final String label;
        Result(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final long sessionId;
    private final long customerId;
    private final long staffId;
    private final String summary;
    private final Category category;
    private final String subCategory;
    private final String customerDemand;
    private final String verifiedInfo;
    private final Info info;
    private final Result result;
    private final String solution;
    private final boolean needFollowup;
    private final LocalDateTime followupTime;
    private final String followupContact;
    private final String attachmentsJson;
    private final SubmitStatus submitStatus;

    /** 历史入口：只传旧六项时，扩展字段留空，兼容既有调用方与测试。 */
    public ChatSessionNote(long sessionId, long customerId, long staffId, String summary,
                           String category, String info, String result, String solution) {
        this(sessionId, customerId, staffId, summary, category, null, null, null,
                info, result, solution, false, null, null, null, SubmitStatus.SUBMITTED);
    }

    /** V61 扩展字段入口（无回访联系方式）：按「提交」语义处理，兼容旧调用方。 */
    public ChatSessionNote(long sessionId, long customerId, long staffId, String summary,
                           String category, String subCategory, String customerDemand, String verifiedInfo,
                           String info, String result, String solution, boolean needFollowup,
                           LocalDateTime followupTime, String attachmentsJson) {
        this(sessionId, customerId, staffId, summary, category, subCategory, customerDemand, verifiedInfo,
                info, result, solution, needFollowup, followupTime, null, attachmentsJson,
                SubmitStatus.SUBMITTED);
    }

    /** 提交时的完整工单：五项必填缺一不可。 */
    public ChatSessionNote(long sessionId, long customerId, long staffId, String summary,
                           String category, String subCategory, String customerDemand, String verifiedInfo,
                           String info, String result, String solution, boolean needFollowup,
                           LocalDateTime followupTime, String followupContact, String attachmentsJson) {
        this(sessionId, customerId, staffId, summary, category, subCategory, customerDemand, verifiedInfo,
                info, result, solution, needFollowup, followupTime, followupContact, attachmentsJson,
                SubmitStatus.SUBMITTED);
    }

    /**
     * 草稿：允许必填项为空，但枚举仍需合法——否则下拉一改，历史草稿就再也提交不了。
     */
    public static ChatSessionNote draft(long sessionId, long customerId, long staffId, String summary,
                                        String category, String subCategory, String customerDemand,
                                        String verifiedInfo, String info, String result, String solution,
                                        boolean needFollowup, LocalDateTime followupTime,
                                        String followupContact, String attachmentsJson) {
        return new ChatSessionNote(sessionId, customerId, staffId, summary, category, subCategory,
                customerDemand, verifiedInfo, info, result, solution, needFollowup, followupTime,
                followupContact, attachmentsJson, SubmitStatus.DRAFT);
    }

    private ChatSessionNote(long sessionId, long customerId, long staffId, String summary,
                            String category, String subCategory, String customerDemand, String verifiedInfo,
                            String info, String result, String solution, boolean needFollowup,
                            LocalDateTime followupTime, String followupContact, String attachmentsJson,
                            SubmitStatus submitStatus) {
        boolean draft = submitStatus == SubmitStatus.DRAFT;
        this.sessionId = sessionId;
        this.customerId = customerId;
        this.staffId = staffId;
        this.summary = draft ? optional(summary) : require(summary, "问题描述");
        this.category = draft ? parseNullable(Category.class, category, "问题分类")
                : parse(Category.class, category, "问题分类");
        this.subCategory = optional(subCategory);
        this.customerDemand = optional(customerDemand);
        this.verifiedInfo = optional(verifiedInfo);
        this.info = draft ? parseNullable(Info.class, info, "明确信息")
                : parse(Info.class, info, "明确信息");
        this.result = draft ? parseNullable(Result.class, result, "处理结果")
                : parse(Result.class, result, "处理结果");
        this.solution = draft ? optional(solution) : require(solution, "解决办法");
        this.needFollowup = needFollowup;
        this.followupTime = followupTime;
        this.followupContact = optional(followupContact);
        // 提交时要求回访必须有联系方式：留空等于「承诺回访但不留号码」，主管没法跟进。
        // 草稿不拦——客服可能先勾上回访，回头再补号码。
        if (!draft && needFollowup && this.followupContact == null) {
            throw new BizException(ErrorCode.SERVICE_TICKET_INCOMPLETE, "回访联系方式不能为空");
        }
        this.attachmentsJson = optional(attachmentsJson);
        this.submitStatus = submitStatus;
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.SERVICE_TICKET_INCOMPLETE, label + "不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 既接受 INQUIRY 这样的稳定码，也接受「商品咨询」这样的中文展示文案。 */
    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        E parsed = parseNullable(type, value, label);
        if (parsed == null) {
            throw new BizException(ErrorCode.SERVICE_TICKET_INCOMPLETE, label + "取值不合法");
        }
        return parsed;
    }

    /** 草稿允许空值，但填了就必须是合法枚举（否则改下拉后历史草稿会永远提交不了）。 */
    private static <E extends Enum<E>> E parseNullable(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException ignored) {
            for (E candidate : type.getEnumConstants()) {
                try {
                    Object labelValue = type.getMethod("getLabel").invoke(candidate);
                    if (text.equals(labelValue)) {
                        return candidate;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("枚举缺少 label: " + type.getName(), e);
                }
            }
        }
        throw new BizException(ErrorCode.SERVICE_TICKET_INCOMPLETE, label + "取值不合法");
    }

    public long getSessionId() { return sessionId; }
    public long getCustomerId() { return customerId; }
    public long getStaffId() { return staffId; }
    public String getSummary() { return summary; }
    public Category getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public String getCustomerDemand() { return customerDemand; }
    public String getVerifiedInfo() { return verifiedInfo; }
    public Info getInfo() { return info; }
    public Result getResult() { return result; }
    public String getSolution() { return solution; }
    public boolean isNeedFollowup() { return needFollowup; }
    public LocalDateTime getFollowupTime() { return followupTime; }
    public String getFollowupContact() { return followupContact; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public SubmitStatus getSubmitStatus() { return submitStatus; }
}
