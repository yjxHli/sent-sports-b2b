package com.sent.sports.customer;

import com.sent.sports.customer.domain.TransferLog;
import com.sent.sports.customer.mapper.TransferLogMapper;
import com.sent.sports.customer.repository.impl.TransferLogRepositoryImpl;
import com.sent.sports.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户改派日志仓储真库集成测试（2026-11-28，P0）。
 *
 * <p>背景：修复前 {@code InMemoryTransferLogRepository} 用 {@code CopyOnWriteArrayList} 存日志，
 * 重启即丢（审计报告 §2.2）。本类同样用「新建仓储实例」模拟重启。</p>
 *
 * <p>表结构差异：{@code customer_owner_log} 只有自增主键，没有业务单号列，
 * 所以领域对象里的 {@code TL...} 号不回写库，读回时用自增 id 作为标识——
 * 这一点在断言里显式体现，避免后来者以为丢了数据。</p>
 */
@org.junit.jupiter.api.condition.EnabledIf("com.sent.sports.support.ItDatabase#available")
class TransferLogRepositoryIT extends BaseIntegrationTest {

    private static final long CUSTOMER_ID = 90002L;

    @Autowired
    private TransferLogMapper transferLogMapper;

    private TransferLogRepositoryImpl freshRepository() {
        return new TransferLogRepositoryImpl(transferLogMapper);
    }

    @Test
    @DisplayName("改派日志落库：换新仓储实例（等价重启）仍能按客户查到")
    void logSurvivesRepositoryRestart() {
        long now = System.currentTimeMillis();
        freshRepository().save(new TransferLog("TL-IT-1", CUSTOMER_ID, 3L, 5L, "客户调整片区", 1L, now));

        assertThat(countOf("customer_owner_log")).as("改派日志必须落表").isEqualTo(1);

        List<TransferLog> reloaded = freshRepository().findByCustomerId(CUSTOMER_ID);
        assertThat(reloaded).as("重启后必须还查得到，说明不再存内存").hasSize(1);

        TransferLog log = reloaded.get(0);
        assertThat(log.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(log.fromStaffId()).isEqualTo(3L);
        assertThat(log.toStaffId()).isEqualTo(5L);
        assertThat(log.reason()).isEqualTo("客户调整片区");
        assertThat(log.operatorId()).isEqualTo(1L);
        assertThat(log.createdAtMillis()).as("创建时间取库里的 created_at，不能是 0").isEqualTo(now);
        assertThat(log.id()).as("表无业务单号，读回用自增 id 兜底").isNotBlank();
    }

    @Test
    @DisplayName("同一客户多条日志按时间倒序返回（最新在前）")
    void returnsNewestFirst() {
        long base = System.currentTimeMillis() - 10_000;
        freshRepository().save(new TransferLog("TL-IT-A", CUSTOMER_ID, 3L, 4L, "第一次", 1L, base));
        freshRepository().save(new TransferLog("TL-IT-B", CUSTOMER_ID, 4L, 5L, "第二次", 1L, base + 5_000));

        List<TransferLog> logs = freshRepository().findByCustomerId(CUSTOMER_ID);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).reason()).as("最新一条应排在最前").isEqualTo("第二次");
        assertThat(logs.get(1).reason()).isEqualTo("第一次");
    }

    @Test
    @DisplayName("其它客户的日志不串台")
    void isolatesByCustomer() {
        freshRepository().save(new TransferLog("TL-IT-X", CUSTOMER_ID, 3L, 4L, "本客户", 1L, 1_000L));
        freshRepository().save(new TransferLog("TL-IT-Y", CUSTOMER_ID + 1, 3L, 4L, "别的客户", 1L, 1_000L));

        assertThat(freshRepository().findByCustomerId(CUSTOMER_ID)).hasSize(1);
        assertThat(freshRepository().findByCustomerId(CUSTOMER_ID + 1)).hasSize(1);
    }

    @Test
    @DisplayName("无改派记录的客户返回空列表")
    void emptyForUnknownCustomer() {
        assertThat(freshRepository().findByCustomerId(999_999L)).isEmpty();
    }
}
