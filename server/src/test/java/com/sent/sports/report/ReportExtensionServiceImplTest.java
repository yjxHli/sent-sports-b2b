package com.sent.sports.report;

import com.sent.sports.report.dto.ReportExtensionDtos;
import com.sent.sports.report.mapper.ReportExtensionMapper;
import com.sent.sports.report.mapper.po.CustomerMixRow;
import com.sent.sports.report.mapper.po.ProductDetailRow;
import com.sent.sports.report.mapper.po.RepeatCustomerRow;
import com.sent.sports.report.service.impl.ReportExtensionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拓展报表服务（TDD 红→绿）：客户采购结构 / 商品销售明细（含毛利）/ 复购新老客结构。
 *
 * <p>SQL 口径与 {@code @DataScope} 由 {@code ReportExtensionMapperIT} 真库校验，
 * 这里只锁时间窗下推、天数/条数收敛、占比与毛利计算、空数据不除零。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportExtensionServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ReportExtensionMapper mapper;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // 2026-11-12 10:00 +08:00
        fixedClock = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZONE);
    }

    private ReportExtensionServiceImpl service() {
        return new ReportExtensionServiceImpl(mapper, fixedClock);
    }

    // ---------- 客户采购结构 ----------

    private static CustomerMixRow mixRow(String type, long customers, long orders, long amount) {
        CustomerMixRow row = new CustomerMixRow();
        row.setCustomerType(type);
        row.setCustomerCount(customers);
        row.setOrderCount(orders);
        row.setAmountCent(amount);
        return row;
    }

    @Test
    @DisplayName("客户结构：时间窗下推、三类齐全、占比按区间总额且为 100%")
    void customerMixWindowAndShare() {
        when(mapper.selectCustomerMix(any(), any())).thenReturn(List.of(
                mixRow("ENTERPRISE", 3, 12, 7_000L),
                mixRow("PERSON", 5, 20, 2_000L),
                mixRow(null, 2, 4, 1_000L)));

        ReportExtensionDtos.CustomerMixVO vo = service().customerMix(7);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectCustomerMix(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 6, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 13, 0, 0));

        assertThat(vo.items()).hasSize(3);
        assertThat(vo.items()).extracting(ReportExtensionDtos.CustomerMixItemVO::type)
                .containsExactly("ENTERPRISE", "PERSON", "UNKNOWN");
        assertThat(vo.items().get(0).label()).isEqualTo("企业客户");
        assertThat(vo.items().get(2).label()).isEqualTo("未分类");
        assertThat(vo.totalAmountCent()).isEqualTo(10_000L);
        assertThat(vo.items()).extracting(ReportExtensionDtos.CustomerMixItemVO::sharePct)
                .containsExactly("70.0", "20.0", "10.0");
    }

    @Test
    @DisplayName("客户结构：区间无成交时三类仍齐全、占比 0.0，不给空环")
    void customerMixEmpty() {
        when(mapper.selectCustomerMix(any(), any())).thenReturn(List.of());

        ReportExtensionDtos.CustomerMixVO vo = service().customerMix(30);

        assertThat(vo.items()).hasSize(3);
        assertThat(vo.items()).extracting(ReportExtensionDtos.CustomerMixItemVO::amountCent)
                .containsExactly(0L, 0L, 0L);
        assertThat(vo.items()).extracting(ReportExtensionDtos.CustomerMixItemVO::sharePct)
                .containsExactly("0.0", "0.0", "0.0");
    }

    // ---------- 商品销售明细 ----------

    private static ProductDetailRow productRow(long skuId, String name, long qty, long amount,
                                               long cost, String sources, int stock) {
        ProductDetailRow row = new ProductDetailRow();
        row.setSkuId(skuId);
        row.setSkuName(name);
        row.setSpec("L / 蓝");
        row.setQty(qty);
        row.setAmountCent(amount);
        row.setCostPriceCent(cost);
        row.setPriceSources(sources);
        row.setStockQty(stock);
        row.setOrderCount(3);
        return row;
    }

    @Test
    @DisplayName("商品明细：按金额降序截断、毛利=成交额-数量×成本、毛利率保留 1 位")
    void productDetailsProfitAndRank() {
        when(mapper.selectProductDetails(any(), any())).thenReturn(List.of(
                productRow(2L, "B", 3, 9_000L, 1_000L, "RETAIL,AGREEMENT", 12),
                productRow(1L, "A", 10, 10_000L, 600L, "RETAIL", 5),
                productRow(3L, "C", 1, 1_000L, 800L, "FLASH_SALE", 0)));

        ReportExtensionDtos.ProductDetailVO vo = service().productDetails(30, 2);

        assertThat(vo.items()).hasSize(2);
        assertThat(vo.items()).extracting(ReportExtensionDtos.ProductDetailItemVO::skuId)
                .containsExactly(1L, 2L);
        // A: 10000 - 10*600 = 4000；B: 9000 - 3*1000 = 6000
        assertThat(vo.items().get(0).profitCent()).isEqualTo(4_000L);
        assertThat(vo.items().get(1).profitCent()).isEqualTo(6_000L);
        assertThat(vo.items().get(0).grossMarginPct()).isEqualTo("40.0");
        assertThat(vo.items().get(0).priceSources()).isEqualTo("RETAIL");
        assertThat(vo.items().get(1).priceSources()).isEqualTo("RETAIL,AGREEMENT");
        assertThat(vo.items().get(1).stockQty()).isEqualTo(12);
        // 汇总恒为全量：总额 20000、总毛利 4000+6000+200=10200
        assertThat(vo.itemCount()).isEqualTo(3);
        assertThat(vo.totalProfitCent()).isEqualTo(10_200L);
        assertThat(vo.grossMarginPct()).isEqualTo("51.0");
    }

    @Test
    @DisplayName("商品明细：条数收敛 1~50、成本为 0 或缺失不炸、空数据毛利率 0.0")
    void productDetailsClampAndEmpty() {
        when(mapper.selectProductDetails(any(), any())).thenReturn(List.of());

        ReportExtensionDtos.ProductDetailVO vo = service().productDetails(999, 0);

        assertThat(vo.days()).isEqualTo(90);
        assertThat(vo.limit()).isEqualTo(1);
        assertThat(vo.items()).isEmpty();
        assertThat(vo.grossMarginPct()).isEqualTo("0.0");
    }

    // ---------- 复购 / 新老客结构 ----------

    private static RepeatCustomerRow repeatRow(long customerId, int periodOrders, long amount,
                                               LocalDateTime firstPaidAt, int totalOrders) {
        RepeatCustomerRow row = new RepeatCustomerRow();
        row.setCustomerId(customerId);
        row.setPeriodOrderCount(periodOrders);
        row.setPeriodAmountCent(amount);
        row.setFirstPaidAt(firstPaidAt);
        row.setTotalOrderCount(totalOrders);
        return row;
    }

    @Test
    @DisplayName("复购结构：区间前已有成交=复购客，区间内首次成交=新客；复购率按人数")
    void repeatCustomerSplit() {
        when(mapper.selectRepeatCustomers(any(), any())).thenReturn(List.of(
                repeatRow(1L, 2, 5_000L, LocalDateTime.of(2026, 10, 1, 0, 0), 7),
                repeatRow(2L, 1, 3_000L, LocalDateTime.of(2026, 10, 20, 0, 0), 3),
                repeatRow(3L, 1, 2_000L, LocalDateTime.of(2026, 11, 8, 0, 0), 1)));

        ReportExtensionDtos.RepeatCustomerVO vo = service().repeatCustomers(7);

        assertThat(vo.totalCustomerCount()).isEqualTo(3);
        assertThat(vo.repeatCustomerCount()).isEqualTo(2);
        assertThat(vo.newCustomerCount()).isEqualTo(1);
        assertThat(vo.repeatRatePct()).isEqualTo("66.7");
        assertThat(vo.repeatAmountCent()).isEqualTo(8_000L);
        assertThat(vo.newAmountCent()).isEqualTo(2_000L);
        assertThat(vo.avgOrderCount()).isEqualTo("1.3");
    }

    @Test
    @DisplayName("复购频次分档：按历史总订单数分 2 单 / 3-5 单 / 6 单以上，只统计复购客")
    void repeatFrequencyBuckets() {
        when(mapper.selectRepeatCustomers(any(), any())).thenReturn(List.of(
                // 复购客，历史 2 单
                repeatRow(1L, 1, 1_000L, LocalDateTime.of(2026, 10, 1, 0, 0), 2),
                // 复购客，历史 4 单
                repeatRow(2L, 1, 2_000L, LocalDateTime.of(2026, 10, 2, 0, 0), 4),
                // 复购客，历史 9 单
                repeatRow(3L, 1, 3_000L, LocalDateTime.of(2026, 10, 3, 0, 0), 9),
                // 新客（首单在本期），历史 1 单，不进频次分布
                repeatRow(4L, 1, 4_000L, LocalDateTime.of(2026, 11, 8, 0, 0), 1)));

        ReportExtensionDtos.RepeatCustomerVO vo = service().repeatCustomers(7);

        assertThat(vo.repeatCustomerCount()).isEqualTo(3);
        assertThat(vo.frequencyBuckets()).hasSize(3);
        assertThat(vo.frequencyBuckets()).extracting(ReportExtensionDtos.FrequencyBucketVO::label)
                .containsExactly("2 单", "3–5 单", "6 单以上");
        assertThat(vo.frequencyBuckets()).extracting(ReportExtensionDtos.FrequencyBucketVO::customerCount)
                .as("三档各 1 人，新客不计入").containsExactly(1L, 1L, 1L);
        assertThat(vo.frequencyBuckets()).extracting(ReportExtensionDtos.FrequencyBucketVO::sharePct)
                .as("分母是复购客 3 人").containsExactly("33.3", "33.3", "33.3");
        assertThat(vo.frequencyBuckets().get(0).amountYuan()).isEqualTo("10.00");
    }

    @Test
    @DisplayName("复购结构：区间无成交时人数与金额归零、三档仍齐全且占比 0.0")
    void repeatCustomerEmpty() {
        when(mapper.selectRepeatCustomers(any(), any())).thenReturn(List.of());

        ReportExtensionDtos.RepeatCustomerVO vo = service().repeatCustomers(30);

        assertThat(vo.totalCustomerCount()).isZero();
        assertThat(vo.repeatRatePct()).isEqualTo("0.0");
        assertThat(vo.avgOrderCount()).isEqualTo("0.0");
        assertThat(vo.frequencyBuckets()).hasSize(3);
        assertThat(vo.frequencyBuckets()).extracting(ReportExtensionDtos.FrequencyBucketVO::customerCount)
                .containsExactly(0L, 0L, 0L);
        assertThat(vo.frequencyBuckets()).extracting(ReportExtensionDtos.FrequencyBucketVO::sharePct)
                .containsExactly("0.0", "0.0", "0.0");
    }
}
