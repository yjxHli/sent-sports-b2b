package com.sent.sports.finance.pdf;

/**
 * 人民币金额大写（发票「价税合计（大写）」栏）。
 * <p>规则依据中国人民银行《正确填写票据和结算凭证的基本规定》：
 * 金额到「元」为止的，在「元」之后写「整」；到「角」为止的，角之后写「整」；
 * 有「分」的，分之后不写「整」；数字中间连续几个零只写一个「零」，末尾的零不写。</p>
 */
public final class RmbUpper {

    private static final String[] DIGIT = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] UNIT4 = {"", "拾", "佰", "仟"};
    private static final String[] GROUP_UNIT = {"", "万", "亿", "万亿"};

    private RmbUpper() {
    }

    /**
     * 分 → 大写金额，如 123456 → 「壹仟贰佰叁拾肆元伍角陆分」。
     * 负数与非法输入一律按 {@code 0} 处理：发票上不允许出现负数金额。
     */
    public static String upper(long cent) {
        if (cent <= 0) {
            return "零元整";
        }
        long yuan = cent / 100;
        int jiao = (int) (cent % 100 / 10);
        int fen = (int) (cent % 10);

        StringBuilder sb = new StringBuilder(integerUpper(yuan)).append("元");
        if (jiao == 0 && fen == 0) {
            return sb.append("整").toString();
        }
        if (jiao == 0) {
            return sb.append("零").append(DIGIT[fen]).append("分").toString();
        }
        sb.append(DIGIT[jiao]).append("角");
        if (fen > 0) {
            return sb.append(DIGIT[fen]).append("分").toString();
        }
        return sb.append("整").toString();
    }

    /** 整数部分大写（不含「元」）。 */
    static String integerUpper(long yuan) {
        if (yuan <= 0) {
            return DIGIT[0];
        }
        int[] groups = new int[4];
        int count = 0;
        long rest = yuan;
        while (rest > 0 && count < groups.length) {
            groups[count++] = (int) (rest % 10000);
            rest /= 10000;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = count - 1; i >= 0; i--) {
            int group = groups[i];
            if (group == 0) {
                // 整节为零：只有后面还有非零节、且当前没有以「零」结尾时才补一个零
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '零') {
                    sb.append('零');
                }
                continue;
            }
            // 本节不足四位（如 200 万里的 200）且不是最高节 → 需要「零」承接
            if (sb.length() > 0 && group < 1000 && sb.charAt(sb.length() - 1) != '零') {
                sb.append('零');
            }
            sb.append(groupUpper(group)).append(GROUP_UNIT[i]);
        }

        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == '零') {
            len--;
        }
        return sb.substring(0, len);
    }

    private static String groupUpper(int group) {
        StringBuilder sb = new StringBuilder();
        int[] digits = {group / 1000, group / 100 % 10, group / 10 % 10, group % 10};
        boolean pendingZero = false;
        boolean started = false;
        for (int i = 0; i < 4; i++) {
            int unitIndex = 3 - i;
            int digit = digits[i];
            if (digit == 0) {
                if (started) {
                    pendingZero = true;
                }
                continue;
            }
            if (pendingZero) {
                sb.append('零');
                pendingZero = false;
            }
            sb.append(DIGIT[digit]).append(UNIT4[unitIndex]);
            started = true;
        }
        return sb.toString();
    }
}
