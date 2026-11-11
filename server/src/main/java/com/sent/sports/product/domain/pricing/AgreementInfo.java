package com.sent.sports.product.domain.pricing;

/** 企业协议价：折扣率百分比（100=不打折）与有效期是否有效。 */
public record AgreementInfo(int discountRate, boolean valid) {
}
