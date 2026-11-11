package com.sent.sports.customize.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** P2-25 定制状态机测试（TDD 红）：覆盖 §15.2；第 4 次驳回转 ESCALATED；未排产可取消。 */
class CustomStateMachineTest {

    @Test
    void submitAndReview() {
        assertEquals(CustomStatus.WAIT_REVIEW, CustomStateMachine.transit(CustomStatus.WAIT_DRAFT, CustomAction.SUBMIT_DRAFT));
        assertEquals(CustomStatus.APPROVED, CustomStateMachine.transit(CustomStatus.WAIT_REVIEW, CustomAction.PASS));
        assertEquals(CustomStatus.REJECTED, CustomStateMachine.transit(CustomStatus.WAIT_REVIEW, CustomAction.REJECT));
    }

    @Test
    void resubmitBeforeThirdReject() {
        // 驳回次数 < 3 → 回到待审核
        assertEquals(CustomStatus.WAIT_REVIEW, CustomStateMachine.transit(CustomStatus.REJECTED, CustomAction.RESUBMIT, 2));
    }

    @Test
    void fourthResubmitEscalates() {
        // 驳回次数 >= 3（第 4 次提交）→ 升级待主管处理
        assertEquals(CustomStatus.ESCALATED, CustomStateMachine.transit(CustomStatus.REJECTED, CustomAction.RESUBMIT, 3));
    }

    @Test
    void productionAndShip() {
        assertEquals(CustomStatus.IN_PRODUCTION, CustomStateMachine.transit(CustomStatus.APPROVED, CustomAction.START_PRODUCTION));
        assertEquals(CustomStatus.FINISHED, CustomStateMachine.transit(CustomStatus.IN_PRODUCTION, CustomAction.FINISH));
        assertEquals(CustomStatus.SHIPPED, CustomStateMachine.transit(CustomStatus.FINISHED, CustomAction.SHIP));
    }

    @Test
    void cancelBeforeProduction() {
        assertEquals(CustomStatus.CANCELLED, CustomStateMachine.transit(CustomStatus.WAIT_DRAFT, CustomAction.CANCEL));
        assertEquals(CustomStatus.CANCELLED, CustomStateMachine.transit(CustomStatus.WAIT_REVIEW, CustomAction.CANCEL));
        assertEquals(CustomStatus.CANCELLED, CustomStateMachine.transit(CustomStatus.REJECTED, CustomAction.CANCEL));
    }

    @Test
    void illegalTransition() {
        // 待提交图稿不能直接审核通过
        BizException ex = assertThrows(BizException.class,
                () -> CustomStateMachine.transit(CustomStatus.WAIT_DRAFT, CustomAction.PASS));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }
}
