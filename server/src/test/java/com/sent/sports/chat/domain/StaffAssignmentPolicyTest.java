package com.sent.sports.chat.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-43 客服分配策略测试（TDD 红）：plan §7.8（验收 A20）。
 * 取会话数最少、并列按工号、达上限跳过、无在线返回空。
 */
class StaffAssignmentPolicyTest {

    private StaffAssignmentPolicy.StaffPresence p(long id, boolean online, int active, int max) {
        return new StaffAssignmentPolicy.StaffPresence(id, online, active, max);
    }

    @Test
    void picksLeastBusy() {
        List<StaffAssignmentPolicy.StaffPresence> staffs = List.of(
                p(1, true, 5, 10),
                p(2, true, 2, 10));
        Optional<StaffAssignmentPolicy.StaffPresence> r = StaffAssignmentPolicy.assign(staffs);
        assertTrue(r.isPresent());
        assertEquals(2L, r.get().staffId());
    }

    @Test
    void tieBrokenByStaffId() {
        List<StaffAssignmentPolicy.StaffPresence> staffs = List.of(
                p(2, true, 0, 10),
                p(1, true, 0, 10));
        Optional<StaffAssignmentPolicy.StaffPresence> r = StaffAssignmentPolicy.assign(staffs);
        assertEquals(1L, r.get().staffId());
    }

    @Test
    void skipsAtCapacity() {
        List<StaffAssignmentPolicy.StaffPresence> staffs = List.of(
                p(1, true, 10, 10),
                p(2, true, 0, 10));
        Optional<StaffAssignmentPolicy.StaffPresence> r = StaffAssignmentPolicy.assign(staffs);
        assertEquals(2L, r.get().staffId());
    }

    @Test
    void noOnlineReturnsEmpty() {
        List<StaffAssignmentPolicy.StaffPresence> staffs = List.of(
                p(1, false, 0, 10),
                p(2, false, 0, 10));
        Optional<StaffAssignmentPolicy.StaffPresence> r = StaffAssignmentPolicy.assign(staffs);
        assertFalse(r.isPresent());
    }

    @Test
    void emptyListReturnsEmpty() {
        assertFalse(StaffAssignmentPolicy.assign(List.of()).isPresent());
    }
}
