package com.sent.sports.product.service.admin;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.admin.FlashSaleDtos;

/**
 * 秒杀活动管理（V54）。
 * <p>活动状态只是运营开关（草稿/已发布/已下线），「进行中 / 已结束」由时间窗现算，
 * 因此不存在"到点了但状态没改"的脏数据。</p>
 */
public interface FlashSaleAdminService {

    PageResult<FlashSaleDtos.ActivityVO> list(PageQuery page, String keyword, String status);

    FlashSaleDtos.ActivityVO detail(Long id);

    FlashSaleDtos.ActivityVO create(FlashSaleDtos.ActivityForm form);

    FlashSaleDtos.ActivityVO update(Long id, FlashSaleDtos.ActivityForm form);

    /** 发布（true）或下线（false）。发布前会重新校验价格、SKU 上下架与时间窗冲突。 */
    FlashSaleDtos.ActivityVO setStatus(Long id, boolean active);

    void delete(Long id);
}
