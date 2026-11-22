package com.sent.sports.product;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.product.dto.admin.FlashSaleDtos;
import com.sent.sports.product.mapper.FlashSaleItemMapper;
import com.sent.sports.product.mapper.FlashSaleMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.FlashSaleActivityPO;
import com.sent.sports.product.mapper.po.FlashSaleItemPO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.service.impl.FlashSaleAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀活动管理（V54）：六条保存校验 + 发布/下线状态流转。
 *
 * <p>这些校验挡的是"活动一旦有问题，下单链路全挂"的场景：
 * 秒杀价高于零售价 → 等于没打折还挂个秒杀标；低于底价 → 下单时 {@code BELOW_FLOOR_PRICE}；
 * 时间窗重叠 → 同一 SKU 取价不确定；含下架 SKU → 前台点进去是死链。</p>
 */
@ExtendWith(MockitoExtension.class)
class FlashSaleAdminServiceImplTest {

    private static final Instant FIXED = Instant.parse("2026-11-21T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 21, 20, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 22, 0, 0);

    @Mock
    private FlashSaleMapper flashSaleMapper;

    @Mock
    private FlashSaleItemMapper flashSaleItemMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private IdGen idGen;

    @Mock
    private Clock clock;

    @InjectMocks
    private FlashSaleAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED);
        lenient().when(clock.getZone()).thenReturn(ZONE);
    }

    private static SkuPO sku(long retailCent, long floorCent, String status) {
        SkuPO po = new SkuPO();
        po.setId(9201L);
        po.setStatus(status);
        po.setRetailPriceCent(retailCent);
        po.setFloorPriceCent(floorCent);
        po.setCostPriceCent(800L);
        return po;
    }

    private static FlashSaleDtos.ActivityForm form(LocalDateTime start, LocalDateTime end, long flashCent) {
        return new FlashSaleDtos.ActivityForm("双十一秒杀", start, end, null,
                List.of(new FlashSaleDtos.ItemForm(9201L, flashCent)));
    }

    private void stubHappyPath() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "ON"));
        when(flashSaleMapper.countOverlap(anyLong(), anyLong(), any(), any())).thenReturn(0);
    }

    // ---------- 校验 ----------

    @Test
    @DisplayName("结束时间不晚于开始时间 → 拒绝")
    void rejectsEndNotAfterStart() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(END, START, 2500L)));
        assertTrue(ex.resolvedMessage().contains("结束时间"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("没有商品 → 拒绝")
    void rejectsEmptyItems() {
        FlashSaleDtos.ActivityForm f = new FlashSaleDtos.ActivityForm(
                "空活动", START, END, null, List.of());
        BizException ex = assertThrows(BizException.class, () -> service.create(f));
        assertTrue(ex.resolvedMessage().contains("至少选择"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("秒杀价不低于零售价 → 拒绝（挂着秒杀标却比原价还贵没有意义）")
    void rejectsFlashPriceNotBelowRetail() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "ON"));

        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(START, END, 10000L)));
        assertTrue(ex.resolvedMessage().contains("低于零售价"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("秒杀价低于底价 → 拒绝（否则下单时必抛 BELOW_FLOOR_PRICE）")
    void rejectsFlashPriceBelowFloor() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 3000L, "ON"));

        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(START, END, 2500L)));
        assertTrue(ex.resolvedMessage().contains("底价"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("同一 SKU 时间窗与已发布活动重叠 → 拒绝")
    void rejectsOverlappingWindow() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "ON"));
        when(flashSaleMapper.countOverlap(anyLong(), anyLong(), any(), any())).thenReturn(1);

        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(START, END, 2500L)));
        assertTrue(ex.resolvedMessage().contains("重叠"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("同一活动里重复添加同一规格 → 拒绝")
    void rejectsDuplicateSku() {
        FlashSaleDtos.ActivityForm f = new FlashSaleDtos.ActivityForm("重复", START, END, null,
                List.of(new FlashSaleDtos.ItemForm(9201L, 2500L),
                        new FlashSaleDtos.ItemForm(9201L, 2600L)));
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "ON"));

        BizException ex = assertThrows(BizException.class, () -> service.create(f));
        assertTrue(ex.resolvedMessage().contains("重复"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("发布时规格已下架 → 拒绝（草稿可以挂下架商品，发布不行）")
    void rejectsOffShelfSkuOnPublish() {
        FlashSaleActivityPO po = new FlashSaleActivityPO();
        po.setId(1L);
        po.setTitle("秒杀");
        po.setStartAt(START);
        po.setEndAt(END);
        po.setStatus("DRAFT");
        when(flashSaleMapper.selectById(1L)).thenReturn(po);

        FlashSaleMapper.ItemRow row = new FlashSaleMapper.ItemRow();
        row.setSkuId(9201L);
        row.setFlashPriceCent(2500L);
        when(flashSaleMapper.selectItems(1L)).thenReturn(List.of(row));
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "OFF"));

        BizException ex = assertThrows(BizException.class, () -> service.setStatus(1L, true));
        assertTrue(ex.resolvedMessage().contains("下架"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("发布时价格校验不通过 → 拒绝，不会把已发布状态写进去")
    void publishRevalidatesPrice() {
        FlashSaleActivityPO po = new FlashSaleActivityPO();
        po.setId(1L);
        po.setTitle("秒杀");
        po.setStartAt(START);
        po.setEndAt(END);
        po.setStatus("DRAFT");
        when(flashSaleMapper.selectById(1L)).thenReturn(po);

        FlashSaleMapper.ItemRow row = new FlashSaleMapper.ItemRow();
        row.setSkuId(9201L);
        row.setFlashPriceCent(20000L);
        when(flashSaleMapper.selectItems(1L)).thenReturn(List.of(row));
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "ON"));

        assertThrows(BizException.class, () -> service.setStatus(1L, true));
        verify(flashSaleMapper, never()).updateById(any(FlashSaleActivityPO.class));
    }

    // ---------- 正常流程 ----------

    @Test
    @DisplayName("create 落草稿：生成活动编号、状态 DRAFT、明细按顺序写入")
    void createSavesDraftWithItems() {
        stubHappyPath();
        when(idGen.next("FS")).thenReturn("FS001");
        doAnswer(inv -> {
            inv.<FlashSaleActivityPO>getArgument(0).setId(1L);
            return 1;
        }).when(flashSaleMapper).insert(any(FlashSaleActivityPO.class));
        when(flashSaleMapper.selectById(1L)).thenAnswer(inv -> {
            FlashSaleActivityPO po = new FlashSaleActivityPO();
            po.setId(1L);
            po.setActivityNo("FS001");
            po.setTitle("双十一秒杀");
            po.setStartAt(START);
            po.setEndAt(END);
            po.setStatus("DRAFT");
            return po;
        });
        when(flashSaleMapper.selectItems(1L)).thenReturn(List.of());

        FlashSaleDtos.ActivityVO vo = service.create(form(START, END, 2500L));

        ArgumentCaptor<FlashSaleActivityPO> activityCaptor = ArgumentCaptor.forClass(FlashSaleActivityPO.class);
        verify(flashSaleMapper).insert(activityCaptor.capture());
        assertEquals("FS001", activityCaptor.getValue().getActivityNo());
        assertEquals("DRAFT", activityCaptor.getValue().getStatus());

        ArgumentCaptor<FlashSaleItemPO> itemCaptor = ArgumentCaptor.forClass(FlashSaleItemPO.class);
        verify(flashSaleItemMapper).insert(itemCaptor.capture());
        assertEquals(1L, itemCaptor.getValue().getActivityId());
        assertEquals(9201L, itemCaptor.getValue().getSkuId());
        assertEquals(2500L, itemCaptor.getValue().getFlashPriceCent());
        assertEquals(0, itemCaptor.getValue().getSort());

        assertEquals("DRAFT", vo.displayStatus());
        assertEquals("FS001", vo.activityNo());
    }

    @Test
    @DisplayName("发布：校验通过后写 ACTIVE")
    void publishSetsActive() {
        FlashSaleActivityPO po = new FlashSaleActivityPO();
        po.setId(1L);
        po.setTitle("秒杀");
        po.setStartAt(START);
        po.setEndAt(END);
        po.setStatus("DRAFT");
        when(flashSaleMapper.selectById(1L)).thenReturn(po);
        FlashSaleMapper.ItemRow row = new FlashSaleMapper.ItemRow();
        row.setSkuId(9201L);
        row.setFlashPriceCent(2500L);
        when(flashSaleMapper.selectItems(1L)).thenReturn(List.of(row));
        when(productMapper.selectSkuById(9201L)).thenReturn(sku(10000L, 1000L, "ON"));
        when(flashSaleMapper.countOverlap(anyLong(), anyLong(), any(), any())).thenReturn(0);

        service.setStatus(1L, true);

        ArgumentCaptor<FlashSaleActivityPO> captor = ArgumentCaptor.forClass(FlashSaleActivityPO.class);
        verify(flashSaleMapper).updateById(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("下线：直接写 OFFLINE，不做价格校验（下线不需要理由）")
    void offlineSetsStatusWithoutValidation() {
        FlashSaleActivityPO po = new FlashSaleActivityPO();
        po.setId(1L);
        po.setStatus("ACTIVE");
        when(flashSaleMapper.selectById(1L)).thenReturn(po);
        when(flashSaleMapper.selectItems(1L)).thenReturn(List.of());

        service.setStatus(1L, false);

        ArgumentCaptor<FlashSaleActivityPO> captor = ArgumentCaptor.forClass(FlashSaleActivityPO.class);
        verify(flashSaleMapper).updateById(captor.capture());
        assertEquals("OFFLINE", captor.getValue().getStatus());
        verify(productMapper, never()).selectSkuById(anyLong());
    }
}
