package com.sent.sports.finance.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 人民币金额大写（发票「价税合计（大写）」栏）。
 * 规则遵循中国人民银行《正确填写票据和结算凭证的基本规定》：
 * 到「元」为止加「整」；有「分」不加「整」；中间连续零只写一个「零」，末尾零不写。
 */
class RmbUpperTest {

    @Test
    @DisplayName("零与整数元")
    void wholeYuan() {
        assertEquals("零元整", RmbUpper.upper(0L));
        assertEquals("壹元整", RmbUpper.upper(100L));
        assertEquals("壹拾元整", RmbUpper.upper(1000L));
        assertEquals("壹佰元整", RmbUpper.upper(10000L));
        assertEquals("贰佰万元整", RmbUpper.upper(200_000_000L));
    }

    @Test
    @DisplayName("角与分：有分不加整，只有角补整")
    void jiaoAndFen() {
        assertEquals("壹仟贰佰叁拾肆元伍角陆分", RmbUpper.upper(123_456L));
        assertEquals("壹佰贰拾叁元肆角整", RmbUpper.upper(12_340L));
        assertEquals("壹佰元零伍分", RmbUpper.upper(10_005L));
    }

    @Test
    @DisplayName("跨万/亿的零处理：连续零只写一个，末尾零不写")
    void zeroHandling() {
        assertEquals("壹万元整", RmbUpper.upper(1_000_000L));
        assertEquals("壹万零壹元整", RmbUpper.upper(1_000_100L));
        assertEquals("壹万零壹佰元整", RmbUpper.upper(1_010_000L));
        assertEquals("壹亿零壹元整", RmbUpper.upper(10_000_000_100L));
        assertEquals("壹亿壹仟万元整", RmbUpper.upper(11_000_000_000L));
    }

    @Test
    @DisplayName("负数与异常输入不抛异常（发票不会出现负数，防御性兜底）")
    void negativeIsGuarded() {
        assertEquals("零元整", RmbUpper.upper(-1L));
    }
}
