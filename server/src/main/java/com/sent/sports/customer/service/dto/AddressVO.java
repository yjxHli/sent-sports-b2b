package com.sent.sports.customer.service.dto;

/**
 * 收货地址视图（用户端地址簿 + 下单快照共用）。
 *
 * <p>既当 Web 响应体，也当跨模块只读契约：order 模块下单时通过
 * {@link com.sent.sports.customer.service.AddressService#findOwned} 拿到这个对象，
 * 再转成 {@code orders.address_snapshot_json}——不直接依赖 customer 的 PO/domain（ArchUnit 规则 4）。</p>
 *
 * @param id        地址主键（快照里不落库，仅地址簿用）
 * @param receiver  收件人
 * @param phone     联系电话
 * @param province  省
 * @param city      市
 * @param district  区/县
 * @param detail    详细地址
 * @param isDefault 是否默认地址（结算页据此预选）
 */
public record AddressVO(Long id, String receiver, String phone,
                        String province, String city, String district, String detail,
                        boolean isDefault) {
}
