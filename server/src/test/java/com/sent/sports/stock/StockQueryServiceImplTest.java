package com.sent.sports.stock;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.stock.dto.admin.AdminStockDtos;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.mapper.po.StockFlowPO;
import com.sent.sports.stock.service.impl.StockQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 库存台账/流水从空桩换成真分页后：四口径换算、预警判定与筛选下推必须可验证。 */
@ExtendWith(MockitoExtension.class)
class StockQueryServiceImplTest {

    @Mock
    private StockMapper stockMapper;

    @Mock
    private StockFlowMapper stockFlowMapper;

    @InjectMocks
    private StockQueryServiceImpl service;

    private static StockMapper.StockLedgerRow ledger(long skuId, int physical, int locked,
                                                     int pending, int frozen, int safe) {
        StockMapper.StockLedgerRow row = new StockMapper.StockLedgerRow();
        row.setSkuId(skuId);
        row.setPhysicalQty(physical);
        row.setLockedQty(locked);
        row.setPendingQty(pending);
        row.setFrozenQty(frozen);
        row.setSafetyQty(safe);
        row.setSkuNo("SKU-" + skuId);
        row.setSpecJson("{\"spec\": \"一筒(12只)\"}");
        row.setProductTitle("尤尼克斯 AS-05 比赛级羽毛球");
        return row;
    }

    private static Page<StockMapper.StockLedgerRow> ledgerPage(StockMapper.StockLedgerRow... rows) {
        Page<StockMapper.StockLedgerRow> page = new Page<>(1, 20);
        page.setRecords(List.of(rows));
        page.setTotal(rows.length);
        return page;
    }

    @Test
    @DisplayName("台账可用量 = 实物 − 锁定 − 待发 − 冻结，SKU 名称来自联查，筛选下推到 SQL")
    void listComputesAvailableAndPushesSkuId() {
        when(stockMapper.selectStockPage(
                ArgumentMatchers.<Page<StockMapper.StockLedgerRow>>any(), any()))
                .thenReturn(ledgerPage(ledger(9209L, 100, 10, 5, 5, 20)));

        PageResult<AdminStockDtos.StockRow> result = service.list(new PageQuery(), 9209L);

        assertEquals(1, result.getTotal());
        AdminStockDtos.StockRow row = result.getList().get(0);
        assertEquals(9209L, row.skuId());
        assertEquals("尤尼克斯 AS-05 比赛级羽毛球 · 一筒(12只)", row.skuName());
        assertEquals(100, row.physicalQty());
        assertEquals(80, row.availableQty());
        assertEquals(20, row.safeQty());
        assertFalse(row.warning());
        verify(stockMapper).selectStockPage(
                ArgumentMatchers.<Page<StockMapper.StockLedgerRow>>any(), ArgumentMatchers.eq(9209L));
    }

    @Test
    @DisplayName("可用量下限为 0，且低于安全库存时打预警")
    void listClampsAvailableAndFlagsWarning() {
        when(stockMapper.selectStockPage(
                ArgumentMatchers.<Page<StockMapper.StockLedgerRow>>any(), any()))
                .thenReturn(ledgerPage(ledger(9210L, 10, 8, 6, 4, 20)));

        AdminStockDtos.StockRow row = service.list(new PageQuery(), null).getList().get(0);

        assertEquals(0, row.availableQty());
        assertTrue(row.warning());
    }

    @Test
    @DisplayName("流水映射 change_qty→qty、remark→reason，业务类型 trim 后下推")
    void flowsMapsFieldsAndPushesBizType() {
        StockFlowPO po = new StockFlowPO();
        po.setId(77L);
        po.setSkuId(9209L);
        po.setBizType("INBOUND");
        po.setBizNo("IN-20261110-001");
        po.setChangeQty(30);
        po.setBeforeQty(100);
        po.setAfterQty(130);
        po.setRemark("采购到货");
        po.setCreatedAt(LocalDateTime.of(2026, 11, 10, 14, 30));

        Page<StockFlowPO> page = new Page<>(1, 20);
        page.setRecords(List.of(po));
        page.setTotal(1);
        when(stockFlowMapper.selectFlowPage(ArgumentMatchers.<Page<StockFlowPO>>any(), any(), any()))
                .thenReturn(page);

        PageResult<AdminStockDtos.FlowRow> result = service.flows(new PageQuery(), null, " INBOUND ");

        AdminStockDtos.FlowRow row = result.getList().get(0);
        assertEquals(77L, row.flowId());
        assertEquals("INBOUND", row.bizType());
        assertEquals(30, row.qty());
        assertEquals(130, row.afterQty());
        assertEquals("采购到货", row.reason());
        assertTrue(row.createdAt() > 0);
        verify(stockFlowMapper).selectFlowPage(
                ArgumentMatchers.<Page<StockFlowPO>>any(), ArgumentMatchers.isNull(),
                ArgumentMatchers.eq("INBOUND"));
    }
}
