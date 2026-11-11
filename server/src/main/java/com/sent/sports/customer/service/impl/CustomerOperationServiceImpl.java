package com.sent.sports.customer.service.impl;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.CustomerOperationService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 客户运营命令真库实现。
 *
 * <p>三个方法都标 {@link UseCase}，由切面用 TransactionTemplate 包住「条件更新 + 流水写入」，
 * 保证不会出现客户改了但流水没落的半截状态。读客户用 {@code selectById} 而非分页查询，
 * 与 {@code CustomerQueryServiceImpl} 的读侧口径一致（软删即不存在）。</p>
 */
@Service
public class CustomerOperationServiceImpl implements CustomerOperationService {

    /** 与 customer.level_code 真实值域对齐（V3 种子：NORMAL / LV1 / LV2 / LV3）。 */
    private static final Set<String> LEVEL_CODES = Set.of("NORMAL", "LV1", "LV2", "LV3");

    /** P5 绑定当前用户前的阶段占位；不写进 staff_id（0 不是真实员工）。 */
    private static final Long OPERATOR_PLACEHOLDER = null;

    private final CustomerMapper customerMapper;

    public CustomerOperationServiceImpl(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Override
    @UseCase
    public AdminCustomerDtos.TransferVO transfer(long customerId, long toStaffId, String reason) {
        requireReason(reason);
        if (toStaffId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标员工不合法");
        }
        CustomerPO po = requireCustomer(customerId);
        long from = po.getOwnerStaffId() == null ? 0L : po.getOwnerStaffId();
        if (customerMapper.updateOwnerStaff(customerId, toStaffId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        customerMapper.insertOperationLog(new CustomerMapper.OperationLogRow(
                OPERATOR_PLACEHOLDER, "CUSTOMER", "TRANSFER", "CUSTOMER", String.valueOf(customerId),
                reason,
                "{\"fromStaffId\":" + from + ",\"toStaffId\":" + toStaffId + "}",
                OPERATOR_PLACEHOLDER));
        return new AdminCustomerDtos.TransferVO(customerId, from, toStaffId);
    }

    @Override
    @UseCase
    public AdminCustomerDtos.LevelVO grantLevel(long customerId, String level,
                                                long validToMillis, String reason) {
        requireReason(reason);
        String normalized = level == null ? "" : level.trim().toUpperCase();
        // 旧前端下发 SILVER/GOLD/PLATINUM/VIP，后端 MemberLevel.valueOf 会抛异常 → 50001。
        // 这里显式收敛为参数错误，避免把「选错等级」升级成系统异常。
        if (!LEVEL_CODES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "等级码不合法：" + level + "（可选 NORMAL / LV1 / LV2 / LV3）");
        }
        CustomerPO po = requireCustomer(customerId);
        String before = po.getLevelCode() == null ? "NORMAL" : po.getLevelCode();
        if (customerMapper.updateLevelCode(customerId, normalized) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        customerMapper.insertOperationLog(new CustomerMapper.OperationLogRow(
                OPERATOR_PLACEHOLDER, "CUSTOMER", "GRANT_LEVEL", "CUSTOMER", String.valueOf(customerId),
                reason,
                "{\"beforeLevel\":\"" + before + "\",\"level\":\"" + normalized
                        + "\",\"validToMillis\":" + validToMillis + "}",
                OPERATOR_PLACEHOLDER));
        return new AdminCustomerDtos.LevelVO(customerId, normalized, validToMillis);
    }

    @Override
    @UseCase
    public AdminCustomerDtos.PointVO adjustPoints(long customerId, long delta, String reason,
                                                  String bizType, String bizNo) {
        requireReason(reason);
        if (delta == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整积分不能为 0");
        }
        CustomerPO po = requireCustomer(customerId);
        long before = po.getPointsBalance() == null ? 0L : po.getPointsBalance();
        long after = before + delta;
        if (customerMapper.addPoints(customerId, delta) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        customerMapper.insertPointLog(new CustomerMapper.PointLogRow(
                customerId, delta, after, "MANUAL",
                blankToNull(bizType), blankToNull(bizNo), 0L));
        return new AdminCustomerDtos.PointVO(customerId, before, after, delta);
    }

    private CustomerPO requireCustomer(long customerId) {
        CustomerPO po = customerMapper.selectById(customerId);
        if (po == null || Integer.valueOf(1).equals(po.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return po;
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原因不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
