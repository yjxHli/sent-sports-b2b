package com.sent.sports.common.domain;

/**
 * 确认短语校验（plan §10.1 / R-04）：危险操作前需用户输入与 action 完全一致的短语。
 * 不完全匹配时上层返回 CONFIRM_TOKEN_INVALID(40302)。
 */
public final class ConfirmPhrase {

    private ConfirmPhrase() {
    }

    /** 精确匹配：inputText 与 action 对应短语完全一致（区分大小写，null 视为不匹配）。 */
    public static boolean matches(ConfirmAction action, String inputText) {
        return inputText != null && inputText.equals(action.phrase());
    }

    public static String phraseOf(ConfirmAction action) {
        return action.phrase();
    }
}
