package com.sent.sports.customer.service.impl;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.CustomerPointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 积分台账真库实现（V3 的 {@code customer.points_balance} + {@code point_log}）。
 *
 * <p>写入顺序与成长值一致：<b>先用唯一键抢「首次入账」的名额，再累加余额</b>。
 * 反过来做的话，重放请求会先加钱再发现流水重复，余额就被多算了一次。</p>
 *
 * <p>不用分布式锁，也不做「先查后写」：并发下的正确性完全由 V47 唯一键保证
 * （{@code INSERT IGNORE} 只有一行能插入成功）。</p>
 *
 * <p><b>扣减（支付抵扣）方向相反</b>：先抢名额，再用「条件更新 + 余额够不够」原子扣减；
 * 扣不动就抛异常让调用方事务回滚（见 {@link #deductPoints}）。</p>
 */
@Service
public class CustomerPointServiceImpl implements CustomerPointService {

    private static final Logger log = LoggerFactory.getLogger(CustomerPointServiceImpl.class);

    /** 抵扣流水的 type 固定 USE：与 ORDER（发放）/ REFUND（扣回）/ MANUAL（人工）区分。 */
    private static final String TYPE_USE = "USE";

    private final CustomerMapper customerMapper;

    public CustomerPointServiceImpl(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Override
    @UseCase
    public long changePoints(long customerId, long delta, String sourceType, String sourceId) {
        if (delta == 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "积分变动不能为 0");
        }
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            // 没有来源单号的流水无法幂等，宁可拒绝也不写一条永远查不到出处的账
            throw new BizException(ErrorCode.PARAM_INVALID, "积分变动必须带来源类型与来源单号");
        }
        CustomerPO customer = customerMapper.selectById(customerId);
        if (customer == null || Integer.valueOf(1).equals(customer.getDeleted())) {
            // 客户不存在时不制造孤立流水（集成测试造的纯订单场景会走这里）
            return 0L;
        }
        long before = customer.getPointsBalance() == null ? 0L : customer.getPointsBalance();
        long after = before + delta;
        int inserted = customerMapper.insertPointLogIfAbsent(new CustomerMapper.PointLogRow(
                customerId, delta, after, sourceType, sourceType, sourceId, 0L));
        if (inserted == 0) {
            return before; // 同一来源重放：不再累加余额
        }
        if (customerMapper.addPoints(customerId, delta) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "客户不存在，积分变动失败");
        }
        return after;
    }

    @Override
    @UseCase
    public long deductPoints(long customerId, long points, String sourceType, String sourceId) {
        if (points <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "扣减积分必须为正数");
        }
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "积分变动必须带来源类型与来源单号");
        }

        // 1) 先抢幂等名额：同一订单只允许一条 USE 流水。
        //    重放（渠道重复通知 / 人工重试）在这里就被挡住，不会二次扣减。
        //    balance_after 只能扣完才知道，先写 0 占位，第 3 步回填——整段在同一个事务里，
        //    外部永远看不到这个占位值。
        int claimed = customerMapper.insertPointLogIfAbsent(new CustomerMapper.PointLogRow(
                customerId, -points, 0L, TYPE_USE, sourceType, sourceId, 0L));
        if (claimed == 0) {
            return currentBalance(customerId);
        }

        // 2) 原子扣减：condition update 是并发防超扣的唯一手段（不做「先查后写」）。
        //    余额不够时影响行数为 0，绝不能靠 Math.max(0, …) 兜成 0 —— 那等于白送抵扣。
        if (customerMapper.deductPoints(customerId, points) == 0) {
            long balance = currentBalance(customerId);
            log.error("[积分风险] 抵扣扣减失败：余额不足或客户不存在 customerId={} need={} balance={} source={}/{}",
                    customerId, points, balance, sourceType, sourceId);
            // 异常抛给调用方（支付回调）：整笔事务回滚，占位流水一并撤销，订单退回待付款
            throw new BizException(ErrorCode.POINT_NOT_ENOUGH, "积分余额不足，抵扣失败");
        }

        // 3) 回填扣后余额快照
        long after = currentBalance(customerId);
        customerMapper.updatePointLogBalance(customerId, sourceType, sourceId, after);
        return after;
    }

    private long currentBalance(long customerId) {
        Long balance = customerMapper.pointsBalanceOf(customerId);
        return balance == null ? 0L : balance;
    }

    @Override
    public long deductedPoints(long customerId, String sourceType, String sourceId) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询已扣积分必须带来源类型与来源单号");
        }
        Long deducted = customerMapper.deductedPointsOf(customerId, sourceType, sourceId);
        return deducted == null ? 0L : deducted;
    }
}
