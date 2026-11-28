package com.sent.sports.job;

import com.sent.sports.product.service.FootprintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 足迹过期清理任务（2026-11-28，TDD 红）。
 *
 * <p>只做一件事：把 {@code FootprintService.cleanupExpired()} 的删除条数透出来打日志。
 * 之所以单独测，是因为「删了 0 行」和「删了 N 行」在运维视角是两个信号，
 * 不能把返回值吞掉——出了问题要能从日志看出来这一轮到底删没删。</p>
 */
@ExtendWith(MockitoExtension.class)
class FootprintCleanupJobTest {

    @Mock
    private FootprintService footprintService;

    @InjectMocks
    private FootprintCleanupJob job;

    @Test
    @DisplayName("执行一次清理：调用服务并返回删除行数")
    void runOnceDelegatesToService() {
        when(footprintService.cleanupExpired()).thenReturn(6);

        assertEquals(6, job.runOnce());
        verify(footprintService).cleanupExpired();
    }

    @Test
    @DisplayName("没有过期记录时返回 0，不抛错（幂等：重复执行删 0 行）")
    void runOnceIsIdempotentWhenNothingExpired() {
        when(footprintService.cleanupExpired()).thenReturn(0);

        assertEquals(0, job.runOnce());
        assertEquals(0, job.runOnce());
    }
}
