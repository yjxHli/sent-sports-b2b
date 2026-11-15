package com.sent.sports.customer.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 收货地址持久化对象（{@code customer_address}，V3 建表）。
 *
 * <p>{@code is_default} 用 {@code Integer} 而非 {@code Boolean}：MySQL 侧是 TINYINT，
 * 兄弟表的 {@code custom_flag} 也是同一写法，转换放在 service 层，PO 只承载行形状。</p>
 */
@TableName("customer_address")
public class CustomerAddressPO extends BasePO {

    private Long customerId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Integer isDefault;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }
}
