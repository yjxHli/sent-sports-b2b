package com.sent.sports.common.domain;

/**
 * 危险操作类型（plan §10.1）：每个 action 对应唯一确认短语，后端校验完全一致才放行。
 */
public enum ConfirmAction {

    ORDER_CLOSE("确认关闭"),
    PAYMENT_WRITE_OFF("确认核销"),
    PAYMENT_REVERSE("确认冲销"),
    ENTERPRISE_CREDIT("确认授信"),
    STOCK_CHECK_APPROVE("确认调整"),
    REFUND_APPROVE("确认退款"),
    STAFF_LOGOUT_ALL("确认下线");

    private final String phrase;

    ConfirmAction(String phrase) {
        this.phrase = phrase;
    }

    /** 该操作要求的确认短语（精确匹配，区分大小写）。 */
    public String phrase() {
        return phrase;
    }
}
