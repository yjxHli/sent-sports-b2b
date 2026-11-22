package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.dto.BatchDeleteOrdersResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 我的订单「多选删除」（客户侧；管理端批量删是 {@link OrderBatchDeleteUseCase}，两者写的是不同列）。
 *
 * <p>业务口径与单条删除 {@link DeleteOrderUseCase} 完全一致（软删 {@code customer_deleted_at}、
 * 只在终态可删、越权按不存在处理），差别只在**一批里既有能删的也有不能删的**时的处理方式：</p>
 * <ul>
 *   <li>能删的照删，不能删的逐条回报原因——不能因为购物车里还躺着一张待收货的单就让整批失败，
 *       否则用户得自己一个个试错；</li>
 *   <li>归属与存在性都写在 SQL 里（{@code customer_id = ? AND customer_deleted_at IS NULL}），
 *       他人单号查不出来，与单条删除的 404 口径一致，不泄露「这个单号存在」；</li>
 *   <li>条件更新再带一次终态与未删条件：并发下被改态/被删过的单影响 0 行，
 *       按幂等成功返回（{@code deleted = 0}），不报错也就不需要前端做重试。</li>
 * </ul>
 */
@Service
public class BatchDeleteOrdersUseCase {

    /** 在途单跳过原因（与单条删除同一句话，前端可直接展示）。 */
    static final String REASON_IN_FLIGHT = "在途订单不能删除，请先完成收货或取消订单";

    /** 不存在与他人订单共用一句话：不能让人拿单号来探存在性。 */
    static final String REASON_NOT_FOUND = "订单不存在或已删除";

    private final OrderMapper orderMapper;

    public BatchDeleteOrdersUseCase(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 批量删除选中的订单。
     *
     * @param orderNos   前端勾选的订单号（乱序、重复、空白都会由 {@link BatchIds#normalize} 归一）
     * @param customerId 当前登录客户；为空表示未登录（管理员不走本用例）
     * @return 实际删除条数 + 未删除明细（前端据此提示「3 单已删除，1 单在途未删」）
     * @throws BizException 未登录 401、未选或超过上限 400
     */
    @UseCase
    public BatchDeleteOrdersResult delete(List<String> orderNos, Long customerId) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        // 归一化放在身份校验之后、查库之前：空选择不该产生任何 SQL
        List<String> wanted = BatchIds.normalize(orderNos);

        Map<String, OrderPO> found = orderMapper.selectByOrderNosForCustomer(customerId, wanted).stream()
                .collect(Collectors.toMap(OrderPO::getOrderNo, o -> o, (a, b) -> a, LinkedHashMap::new));

        List<String> deletable = new ArrayList<>(wanted.size());
        List<BatchDeleteOrdersResult.Skipped> skipped = new ArrayList<>();
        for (String orderNo : wanted) {
            OrderPO order = found.get(orderNo);
            if (order == null) {
                skipped.add(new BatchDeleteOrdersResult.Skipped(orderNo, REASON_NOT_FOUND));
            } else if (!DeleteOrderUseCase.DELETABLE_STATUS.contains(order.getStatus())) {
                skipped.add(new BatchDeleteOrdersResult.Skipped(orderNo, REASON_IN_FLIGHT));
            } else {
                deletable.add(orderNo);
            }
        }

        // 一条都删不了就别发 UPDATE：空 IN () 是语法错误，且没必要白跑一次写事务
        if (deletable.isEmpty()) {
            return new BatchDeleteOrdersResult(0, List.copyOf(skipped));
        }
        int deleted = orderMapper.batchMarkCustomerDeleted(
                customerId, deletable, DeleteOrderUseCase.DELETABLE_STATUSES);
        return new BatchDeleteOrdersResult(deleted, List.copyOf(skipped));
    }
}
