package com.sent.sports.finance.repository;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.domain.Receivable;
import com.sent.sports.finance.domain.ReceivableStatus;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 应收仓储实现（P5-21）。
 * <p>{@code save} 只做「按主键更新进度」，因为应收的产生源于订单落账（订单侧带 order_no 才能定位唯一行）；
 * 若传入未落库（id=0）的应收，说明调用链走错了，直接以 {@code PARAM_INVALID} 暴露，避免静默丢数据。
 */
@Repository
public class ReceivableRepositoryImpl implements ReceivableRepository {

    private final ReceivableMapper receivableMapper;

    public ReceivableRepositoryImpl(ReceivableMapper receivableMapper) {
        this.receivableMapper = receivableMapper;
    }

    @Override
    public void save(Receivable receivable) {
        if (receivable.getId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "应收须由订单落账创建，不可直接新增");
        }
        if (receivableMapper.updateProgress(receivable.getId(), receivable.getReceivedCent(),
                receivable.getStatus().name()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    public Optional<Receivable> findById(long id) {
        ReceivablePO po = receivableMapper.selectById(id);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public List<Receivable> findAllByCustomerId(long customerId) {
        List<ReceivablePO> pos = receivableMapper.selectOpenByCustomer(customerId);
        List<Receivable> list = new ArrayList<>(pos.size());
        for (ReceivablePO po : pos) {
            list.add(toDomain(po));
        }
        return list;
    }

    private Receivable toDomain(ReceivablePO po) {
        return new Receivable(po.getId(), po.getAmountCent(), po.getReceivedCent(),
                po.getDueDate(), ReceivableStatus.valueOf(po.getStatus()));
    }
}
