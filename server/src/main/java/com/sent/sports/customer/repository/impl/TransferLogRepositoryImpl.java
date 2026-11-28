package com.sent.sports.customer.repository.impl;

import com.sent.sports.common.util.TimeUtil;
import com.sent.sports.customer.domain.TransferLog;
import com.sent.sports.customer.mapper.TransferLogMapper;
import com.sent.sports.customer.mapper.po.TransferLogPO;
import com.sent.sports.customer.repository.TransferLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 客户改派日志仓储（真库，2026-11-28 P0 修复）。
 *
 * <p>替换原先的 {@code InMemoryTransferLogRepository}：改派历史原来只存在进程内的
 * {@code CopyOnWriteArrayList} 里，重启即丢，与「操作留痕可追溯」的审计要求冲突。
 * 现在落 V3 就建好、但一直空置的 {@code customer_owner_log}。
 *
 * <p>两处字段口径：
 * <ul>
 *   <li>表没有业务单号列，领域对象的 {@code TL...} 不落库，读回时用自增 id 作为标识；</li>
 *   <li>{@code from/to_staff_id}、{@code operator_id} 在领域里用 0 表示「无」，库里存 NULL
 *       —— 0 不是真实员工，落 NULL 才不会在管理端显示成「员工 0」。</li>
 * </ul>
 */
@Repository
public class TransferLogRepositoryImpl implements TransferLogRepository {

    private final TransferLogMapper transferLogMapper;

    public TransferLogRepositoryImpl(TransferLogMapper transferLogMapper) {
        this.transferLogMapper = transferLogMapper;
    }

    @Override
    public void save(TransferLog log) {
        TransferLogPO po = new TransferLogPO();
        po.setCustomerId(log.customerId());
        po.setFromStaffId(log.fromStaffId() > 0 ? log.fromStaffId() : null);
        po.setToStaffId(log.toStaffId() > 0 ? log.toStaffId() : null);
        po.setReason(log.reason());
        po.setOperatorId(log.operatorId() > 0 ? log.operatorId() : null);
        // 显式写 created_at：领域对象带着「发生时刻」，而 MetaObjectHandler 只在字段为 null 时兜底，
        // 显式赋值才能保证库里的时间就是业务发生的时间，不受 save 时机影响。
        po.setCreatedAt(TimeUtil.fromMillis(log.createdAtMillis()));
        transferLogMapper.insert(po);
    }

    @Override
    public List<TransferLog> findByCustomerId(long customerId) {
        return transferLogMapper.selectByCustomerId(customerId).stream()
                .map(po -> new TransferLog(
                        String.valueOf(po.getId()),
                        po.getCustomerId() == null ? 0L : po.getCustomerId(),
                        po.getFromStaffId() == null ? 0L : po.getFromStaffId(),
                        po.getToStaffId() == null ? 0L : po.getToStaffId(),
                        po.getReason(),
                        po.getOperatorId() == null ? 0L : po.getOperatorId(),
                        TimeUtil.toMillis(po.getCreatedAt())))
                .toList();
    }
}
