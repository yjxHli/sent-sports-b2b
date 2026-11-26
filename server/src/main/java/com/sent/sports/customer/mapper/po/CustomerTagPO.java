package com.sent.sports.customer.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 客户标签定义（{@code customer_tag}，V63）。
 *
 * <p>「有哪些标签」由后台维护，客服只能选不能造——否则一人一套说法，
 * 「犹豫观望」和「还在考虑」会同时出现在不同客服的屏幕上，标签就失去检索意义。</p>
 */
@TableName("customer_tag")
public class CustomerTagPO extends BasePO {

    /** 标签名，全表唯一（含已停用：停用的名字不能被重新占用，否则历史记录对不上）。 */
    private String name;

    /** EXCELLENT / NEUTRAL / RISK / MALICIOUS（见 {@code TagCategory}）。 */
    private String category;

    /** ACTIVE 可在弹窗里选 / DISABLED 不再可选，但已打过的标签仍展示。 */
    private String status;

    /** 同分类内的展示顺序。 */
    private Integer sortNo;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
