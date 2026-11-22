package com.sent.sports.customize;

import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;
import com.sent.sports.customize.service.CustomRefundPolicy;
import com.sent.sports.customize.service.impl.CustomizeRefundGatewayImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 定制退款判定门面（TDD 红，2026-11-21 用户口径）。
 *
 * <p>用户给的三档规则：</p>
 * <ul>
 *   <li><b>可自助退</b>：WAIT_DRAFT（待传图稿）/ WAIT_REVIEW（待审核）/ REJECTED（已驳回）——
 *       还没进机器，客户自己点退款即可；</li>
 *   <li><b>可申请但要人工</b>：APPROVED（审核通过未排产）——图纸已过审但没投产，
 *       让客服确认「是否已备料/打样」再决定，不硬拦也不放任；ESCALATED（待主管处理）同理；</li>
 *   <li><b>绝对拦截</b>：IN_PRODUCTION / FINISHED / SHIPPED——已经进机器或出货，退不了。</li>
 * </ul>
 *
 * <p>门面必须一次给出完整判定（而不是「是否拦截」布尔），因为 APPROVED 既不是随便退、
 * 也不是不能退，布尔表达不了这一档。</p>
 */
class CustomizeRefundGatewayImplTest extends BaseUnitTest {

    @Mock
    private CustomizeRepository customizeRepository;

    private CustomizeRefundGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new CustomizeRefundGatewayImpl(customizeRepository);
    }

    @ParameterizedTest
    @EnumSource(value = CustomStatus.class, names = {"WAIT_DRAFT", "WAIT_REVIEW", "REJECTED"})
    @DisplayName("审核通过前（待传图稿 / 待审核 / 已驳回）：用户可自助申请退款")
    void preApprovalStatusesAreSelfService(CustomStatus status) {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.of(status));

        assertEquals(CustomRefundPolicy.SELF_SERVICE, gateway.policyOf("SO1", 1L),
                status + " 阶段还没进机器，应当允许用户自己申请退款");
    }

    @ParameterizedTest
    @EnumSource(value = CustomStatus.class, names = {"IN_PRODUCTION", "FINISHED", "SHIPPED"})
    @DisplayName("进入生产后（生产中 / 已完工 / 已发货）：绝对拦截")
    void productionStatusesAreBlocked(CustomStatus status) {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.of(status));

        assertEquals(CustomRefundPolicy.BLOCKED, gateway.policyOf("SO1", 1L),
                status + " 已进入生产链路，退款必须被拦下");
    }

    @Test
    @DisplayName("审核通过但未排产（APPROVED）：允许申请，但要人工复核")
    void approvedRequiresManualReview() {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.of(CustomStatus.APPROVED));

        assertEquals(CustomRefundPolicy.MANUAL_REVIEW, gateway.policyOf("SO1", 1L),
                "APPROVED 还没进机器，可以退，但必须由客服确认是否已备料");
    }

    @Test
    @DisplayName("待主管处理（ESCALATED）：交人工判断，既不自助放行也不硬拦")
    void escalatedFallsBackToManualReview() {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.of(CustomStatus.ESCALATED));

        assertEquals(CustomRefundPolicy.MANUAL_REVIEW, gateway.policyOf("SO1", 1L));
    }

    @Test
    @DisplayName("定制已取消：没有可生产的东西，按自助退处理")
    void cancelledIsSelfService() {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.of(CustomStatus.CANCELLED));

        assertEquals(CustomRefundPolicy.SELF_SERVICE, gateway.policyOf("SO1", 1L));
    }

    @Test
    @DisplayName("订单行根本没有定制：不受定制退款规则约束")
    void noCustomRecordIsNotCustom() {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.empty());

        assertEquals(CustomRefundPolicy.NOT_CUSTOM, gateway.policyOf("SO1", 1L));
    }

    @Test
    @DisplayName("NONE（行上有定制标记但未生成定制单）：同样按非定制处理")
    void noneStatusIsNotCustom() {
        when(customizeRepository.statusOf("SO1", 1L)).thenReturn(Optional.of(CustomStatus.NONE));

        assertEquals(CustomRefundPolicy.NOT_CUSTOM, gateway.policyOf("SO1", 1L));
    }
}
