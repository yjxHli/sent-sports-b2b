package com.sent.sports.customer.service.dto;

/**
 * 新增 / 修改收货地址指令。
 *
 * @param receiver  收件人（必填）
 * @param phone     联系电话（必填）
 * @param province  省
 * @param city      市
 * @param district  区/县
 * @param detail    详细地址（必填）
 * @param isDefault 是否设为默认地址
 */
public record AddressCmd(String receiver, String phone,
                         String province, String city, String district, String detail,
                         boolean isDefault) {
}
