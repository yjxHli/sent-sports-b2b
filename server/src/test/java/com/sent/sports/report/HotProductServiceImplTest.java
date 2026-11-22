package com.sent.sports.report;

import com.sent.sports.report.dto.HotProductDtos;
import com.sent.sports.report.mapper.HotProductMapper;
import com.sent.sports.report.mapper.po.HotProductRow;
import com.sent.sports.report.service.impl.HotProductServiceImpl;
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
 * 热销商品服务（TDD 2026-11-21）：时间窗下推、天数/条数收敛、Top N 与名次、
 * 占比分母是区间全量（不受 limit 影响）、商品名回落、空数据。
 *
 * <p>SQL 口径与数据范围由 {@code HotProductMapperIT} 真库校验，这里只锁服务层映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class HotProductServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private HotProductMapper mapper;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // 2026-11-12 10:00 +08:00
        fixedClock = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZONE);
    }

    private HotProductServiceImpl service() {
        return new HotProductServiceImpl(mapper, fixedClock);
    }

    private static HotProductRow row(long skuId, String name, String spec, long qty,
                                     long amountCent, int orderCount) {
        HotProductRow row = new HotProductRow();
        row.setSkuId(skuId);
        row.setSkuName(name);
        row.setSpec(spec);
        row.setQty(qty);
        row.setAmountCent(amountCent);
        row.setOrderCount(orderCount);
        return row;
    }

    @Test
    @DisplayName("近 7 天时间窗是 [11-06 00:00, 11-13 00:00)，由 SQL 下推")
    void pushesDateWindow() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of());

        service().hotProducts(7, 5);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectHotProducts(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 6, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 13, 0, 0));
    }

    @Test
    @DisplayName("天数收敛 1~90、条数收敛 1~50，避免一次扫描过大区间与超长榜")
    void clampsDaysAndLimit() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of());

        service().hotProducts(0, 0);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectHotProducts(from.capture(), any());
        assertThat(from.getValue()).as("days=0 收敛为 1 天").isEqualTo(LocalDateTime.of(2026, 11, 12, 0, 0));

        HotProductDtos.HotProductVO clamped = service().hotProducts(999, 999);
        assertThat(clamped.days()).isEqualTo(90);
        assertThat(clamped.limit()).isEqualTo(50);
    }

    @Test
    @DisplayName("Top N 按金额降序截断，名次从 1 连续；服务层排序不依赖 SQL ORDER BY")
    void truncatesToTopNAndRanks() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of(
                row(3L, "C", "红", 1, 1_000L, 1),
                row(1L, "A", "蓝", 9, 9_000L, 4),
                row(2L, "B", "绿", 5, 4_000L, 2)));

        HotProductDtos.HotProductVO vo = service().hotProducts(30, 2);

        assertThat(vo.items()).hasSize(2);
        assertThat(vo.items()).extracting(HotProductDtos.HotProductItemVO::skuId)
                .as("按金额降序：9000 > 4000").containsExactly(1L, 2L);
        assertThat(vo.items()).extracting(HotProductDtos.HotProductItemVO::rank)
                .containsExactly(1, 2);
        assertThat(vo.items().get(0).amountYuan()).isEqualTo("90.00");
    }

    @Test
    @DisplayName("总额 / 总件数 / SKU 数恒为全量口径，不随 limit 变小")
    void totalsStayFullRange() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of(
                row(1L, "A", "蓝", 10, 10_000L, 3),
                row(2L, "B", "绿", 5, 4_000L, 2),
                row(3L, "C", "红", 1, 1_000L, 1)));

        HotProductDtos.HotProductVO vo = service().hotProducts(30, 2);

        assertThat(vo.totalAmountCent()).isEqualTo(15_000L);
        assertThat(vo.totalAmountYuan()).isEqualTo("150.00");
        assertThat(vo.totalQty()).isEqualTo(16L);
        assertThat(vo.skuCount()).isEqualTo(3);
        assertThat(vo.items()).hasSize(2);
    }

    @Test
    @DisplayName("占比 = 该 SKU 金额 / 区间总金额，保留 1 位小数")
    void computesSharePercent() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of(
                row(1L, "A", "蓝", 1, 3_330L, 1),
                row(2L, "B", "绿", 1, 6_670L, 1)));

        HotProductDtos.HotProductVO vo = service().hotProducts(30, 5);

        // 3330 / 10000 = 33.3%；6670 / 10000 = 66.7%
        assertThat(vo.items().get(1).sharePct()).isEqualTo("33.3");
        assertThat(vo.items().get(0).sharePct()).isEqualTo("66.7");
    }

    @Test
    @DisplayName("商品名为空回落「商品#{skuId}」，规格为空给空串而不是 null")
    void fallsBackForBlankName() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of(
                row(9527L, null, null, 1, 100L, 1),
                row(9528L, "  ", "L", 1, 50L, 1)));

        HotProductDtos.HotProductVO vo = service().hotProducts(30, 5);

        assertThat(vo.items().get(0).skuName()).isEqualTo("商品#9527");
        assertThat(vo.items().get(0).spec()).isEmpty();
        assertThat(vo.items().get(1).skuName()).isEqualTo("商品#9528");
    }

    @Test
    @DisplayName("区间内无成交：items 空、总额与件数归零、不出现除零")
    void emptyRange() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of());

        HotProductDtos.HotProductVO vo = service().hotProducts(30, 5);

        assertThat(vo.items()).isEmpty();
        assertThat(vo.totalAmountCent()).isZero();
        assertThat(vo.totalAmountYuan()).isEqualTo("0.00");
        assertThat(vo.totalQty()).isZero();
        assertThat(vo.skuCount()).isZero();
    }

    @Test
    @DisplayName("金额为 0 的 SKU 仍可上榜，但占比为 0.0，不抛异常")
    void zeroAmountSku() {
        when(mapper.selectHotProducts(any(), any())).thenReturn(List.of(
                row(1L, "赠品", "小样", 3, 0L, 1)));

        HotProductDtos.HotProductVO vo = service().hotProducts(30, 5);

        assertThat(vo.items()).hasSize(1);
        assertThat(vo.items().get(0).sharePct()).isEqualTo("0.0");
    }
}
