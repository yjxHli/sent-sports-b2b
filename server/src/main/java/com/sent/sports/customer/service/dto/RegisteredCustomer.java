package com.sent.sports.customer.service.dto;

/** 注册成功后落库的客户身份（auth 模块只拿这一份 DTO，不碰 customer 的 PO / mapper）。 */
public record RegisteredCustomer(long customerId, String phone, String nickname, String customerNo) {
}
