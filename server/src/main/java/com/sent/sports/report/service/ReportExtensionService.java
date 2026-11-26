package com.sent.sports.report.service;

import com.sent.sports.report.dto.ReportExtensionDtos;

/** 拓展业务报表查询：客户结构 / 商品明细 / 复购结构，统计区间由服务层统一下推。 */
public interface ReportExtensionService {

    ReportExtensionDtos.CustomerMixVO customerMix(int days);

    ReportExtensionDtos.ProductDetailVO productDetails(int days, int limit);

    ReportExtensionDtos.RepeatCustomerVO repeatCustomers(int days);
}
