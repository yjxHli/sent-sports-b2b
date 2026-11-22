package com.sent.sports.customize.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.service.dto.BatchDeleteCustomOrdersResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 我的定制「多选删除」（客户侧；管理端批量删是 {@link CustomBatchDeleteUseCase}，写的是 {@code deleted} 列）。
 *
 * <p>为什么比订单更保守：未发货的定制单承载着图稿与审图 SLA，客户一删，审图台就凭空少一条待办、
 * 生产排期也对不上账。所以只允许 {@link CustomStatus#isTerminal() 终态}（已发货 / 已取消）可删，
 * 其余状态逐条回报「还在进行中」。软删只写 {@code customer_deleted_at}，
 * 图稿、审图日志、财务口径全部照旧可查。</p>
 */
@Service
public class BatchDeleteCustomOrdersUseCase {

    /** 可删状态 = 定制状态机的全部终态（唯一真源在 {@link CustomStatus}，这里不另抄一份）。 */
    public static final List<String> DELETABLE_STATUSES = Arrays.stream(CustomStatus.values())
            .filter(CustomStatus::isTerminal)
            .map(Enum::name)
            .toList();

    private static final String REASON_NOT_FOUND = "定制单不存在或已删除";

    private final CustomOrderMapper customOrderMapper;

    public BatchDeleteCustomOrdersUseCase(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    /**
     * 批量删除选中的定制单。
     *
     * @param customNos  前端勾选的定制单号（乱序、重复、空白由 {@link BatchIds#normalize} 归一）
     * @param customerId 当前登录客户；为空表示未登录
     * @return 实际删除条数 + 未删除明细
     */
    @UseCase
    public BatchDeleteCustomOrdersResult delete(List<String> customNos, Long customerId) {
        if (customerId == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        List<String> wanted = BatchIds.normalize(customNos);

        Map<String, CustomOrderPO> found = customOrderMapper.selectByCustomNosForCustomer(customerId, wanted)
                .stream()
                .collect(Collectors.toMap(CustomOrderPO::getCustomNo, c -> c, (a, b) -> a, LinkedHashMap::new));

        List<String> deletable = new ArrayList<>(wanted.size());
        List<BatchDeleteCustomOrdersResult.Skipped> skipped = new ArrayList<>();
        for (String customNo : wanted) {
            CustomOrderPO order = found.get(customNo);
            if (order == null) {
                skipped.add(new BatchDeleteCustomOrdersResult.Skipped(customNo, REASON_NOT_FOUND));
            } else if (!DELETABLE_STATUSES.contains(order.getStatus())) {
                skipped.add(new BatchDeleteCustomOrdersResult.Skipped(customNo, inProgressReason(order.getStatus())));
            } else {
                deletable.add(customNo);
            }
        }

        if (deletable.isEmpty()) {
            return new BatchDeleteCustomOrdersResult(0, List.copyOf(skipped));
        }
        int deleted = customOrderMapper.batchMarkCustomerDeleted(customerId, deletable, DELETABLE_STATUSES);
        return new BatchDeleteCustomOrdersResult(deleted, List.copyOf(skipped));
    }

    /** 把状态翻成中文再拼进提示，客服与用户看到的是「生产中」而不是 IN_PRODUCTION。 */
    private static String inProgressReason(String status) {
        String name = Arrays.stream(CustomStatus.values())
                .filter(s -> s.name().equals(status))
                .map(CustomStatus::getChineseName)
                .findFirst()
                .orElse("处理中");
        return "定制单还在进行中（" + name + "），完成或取消后才能删除";
    }
}
