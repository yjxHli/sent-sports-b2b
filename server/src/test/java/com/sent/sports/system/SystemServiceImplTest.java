package com.sent.sports.system;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.mapper.OperationLogMapper;
import com.sent.sports.system.mapper.po.OperationLogRow;
import com.sent.sports.system.service.impl.SystemServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 操作日志：写入留痕 + 读侧真查库（列表/筛选/映射）。 */
@ExtendWith(MockitoExtension.class)
class SystemServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @Test
    @DisplayName("recordLog 把六个要素透传到 sys_operation_log")
    void recordLogPersistsAllFields() {
        SystemServiceImpl service = new SystemServiceImpl(operationLogMapper);

        service.recordLog("ORDER", "CLOSE", "ORDER", "SO-1", "客户取消", 9003L);

        verify(operationLogMapper).insert("ORDER", "CLOSE", "ORDER", "SO-1", "客户取消", 9003L);
    }

    @Test
    @DisplayName("listLogs 真查 sys_operation_log 并映射出完整审计视图")
    void listLogsQueriesMapperAndMapsPage() {
        OperationLogRow row = sampleRow();
        stubSelectPage(row);
        when(operationLogMapper.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(1L);

        LogQuery query = new LogQuery();
        query.setPage(1);
        query.setSize(10);
        query.setModule("ORDER");
        query.setAction("CLOSE");
        query.setStaffId(9003L);
        query.setTargetType("ORDER");
        query.setTargetId("SO-1");
        query.setKeyword("取消");

        PageResult<AdminSystemDtos.LogVO> result =
                new SystemServiceImpl(operationLogMapper).listLogs(query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        AdminSystemDtos.LogVO vo = result.getList().get(0);
        assertEquals(7L, vo.logId());
        assertEquals("张三", vo.operator());
        assertEquals("CLOSE", vo.action());
        assertEquals("ORDER", vo.module());
        assertEquals("ORDER", vo.targetType());
        assertEquals("SO-1", vo.targetId());
        assertEquals("客户取消", vo.reason());
        assertEquals("127.0.0.1", vo.ip());
        assertEquals("trace-1", vo.traceId());
        assertEquals("{\"before\":1}", vo.beforeJson());
        assertEquals("{\"after\":2}", vo.afterJson());
        assertEquals(row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.timestampMillis());
    }

    @Test
    @DisplayName("listLogs 把空白筛选归一为 null，避免空条件污染 SQL")
    void listLogsNormalizesBlankFilters() {
        stubSelectPage(null);
        when(operationLogMapper.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        LogQuery query = new LogQuery();
        query.setModule("  ");
        query.setAction("");
        query.setTargetType(" ");
        query.setTargetId("");
        query.setKeyword("   ");

        PageResult<AdminSystemDtos.LogVO> result =
                new SystemServiceImpl(operationLogMapper).listLogs(query);

        assertEquals(0L, result.getTotal());
        assertEquals(0, result.getList().size());
        verify(operationLogMapper).countLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("listLogs 按分页参数构造 Page 并保留独立 count 总数")
    void listLogsKeepsIndependentTotal() {
        stubSelectPage(null);
        when(operationLogMapper.countLogs(any(), any(), any(), any(), any(), any())).thenReturn(37L);

        LogQuery query = new LogQuery();
        query.setPage(3);
        query.setSize(5);

        PageResult<AdminSystemDtos.LogVO> result =
                new SystemServiceImpl(operationLogMapper).listLogs(query);

        assertEquals(37L, result.getTotal());
        assertEquals(3L, result.getPage());
        assertEquals(5L, result.getSize());
        assertEquals(8L, result.getPages());
    }

    private void stubSelectPage(OperationLogRow row) {
        when(operationLogMapper.selectLogPage(any(Page.class), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Page<OperationLogRow> page = invocation.getArgument(0);
                    page.setRecords(row == null ? List.of() : List.of(row));
                    return page;
                });
    }

    private OperationLogRow sampleRow() {
        OperationLogRow row = new OperationLogRow();
        row.setLogId(7L);
        row.setStaffId(9003L);
        row.setOperator("张三");
        row.setModule("ORDER");
        row.setAction("CLOSE");
        row.setTargetType("ORDER");
        row.setTargetId("SO-1");
        row.setReason("客户取消");
        row.setIp("127.0.0.1");
        row.setTraceId("trace-1");
        row.setBeforeJson("{\"before\":1}");
        row.setAfterJson("{\"after\":2}");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 8, 10, 30));
        return row;
    }
}
