package com.sent.sports.product.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动管理接口 DTO（V54）。
 * <p>金额一律「分」。{@code displayStatus} 是由时间窗现算的展示态，
 * 与库内 {@code status}（运营开关）区分开。</p>
 */
public final class FlashSaleDtos {

    private FlashSaleDtos() {
    }

    /** 活动商品行：秒杀价必须大于 0，上限由服务层按零售价/底价校验。 */
    public record ItemForm(
            @NotNull(message = "SKU 不能为空") Long skuId,
            @Min(value = 1, message = "秒杀价必须大于 0") long flashPriceCent) {
    }

    public record ActivityForm(
            @NotBlank(message = "活动标题不能为空") String title,
            @NotNull(message = "开始时间不能为空") LocalDateTime startAt,
            @NotNull(message = "结束时间不能为空") LocalDateTime endAt,
            String remark,
            @NotNull(message = "至少选择一个商品") List<@Valid ItemForm> items) {
    }

    /** 活动商品视图：带出商品名/规格/零售价/底价，编辑页无需再逐个回查。 */
    public record ItemVO(
            String skuId,
            String skuCode,
            String productName,
            String spec,
            long retailPriceCent,
            long floorPriceCent,
            long flashPriceCent) {
    }

    public record ActivityVO(
            String activityId,
            String activityNo,
            String title,
            String status,
            String displayStatus,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String remark,
            int itemCount,
            List<ItemVO> items) {

        /** 列表页不带明细，避免一页活动打出 N 次商品查询。 */
        public ActivityVO(String activityId, String activityNo, String title, String status,
                          String displayStatus, LocalDateTime startAt, LocalDateTime endAt,
                          String remark, int itemCount) {
            this(activityId, activityNo, title, status, displayStatus, startAt, endAt,
                    remark, itemCount, List.of());
        }
    }
}
