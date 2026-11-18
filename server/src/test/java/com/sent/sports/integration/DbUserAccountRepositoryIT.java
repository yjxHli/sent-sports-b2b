package com.sent.sports.integration;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 账号仓储直连 MySQL 的验收：工号不变、登录名可读、失败计数落库、重启等价。 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class DbUserAccountRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private UserAccountRepository repository;

    private void seedAdmin() {
        jdbc.update("INSERT INTO sys_staff (id, staff_no, username, name, phone, password_hash, role_id, "
                        + "status, fail_count, token_version, deleted) "
                        + "VALUES (9001, 'ADMIN001', 'admin', '验收管理员', '13800009001', '123456', 1, "
                        + "'ACTIVE', 0, 0, 0)");
    }

    @Test
    @DisplayName("admin 登录只读 username：staff_no 仍是 ADMIN001，登录名为 admin")
    void adminLoginKeepsStaffNoAndReadsUsername() {
        seedAdmin();

        UserAccount account = repository.findByUsername("admin").orElseThrow();

        assertThat(account.getUserId()).isEqualTo(9001L);
        assertThat(account.getUsername()).isEqualTo("admin");
        assertThat(account.getStaffNo()).as("工号绝不能被登录名覆盖").isEqualTo("ADMIN001");
        assertThat(account.getAud()).isEqualTo(UserAccount.AUD_ADMIN);
        assertThat(repository.findByUsername("ADMIN001")).isEmpty();
        account.authenticate("123456", System.currentTimeMillis());
    }

    @Test
    @DisplayName("登录失败计数和锁定时间写回数据库（不依赖进程内仓储）")
    void failureStateIsPersisted() {
        seedAdmin();
        UserAccount account = repository.findByUsername("admin").orElseThrow();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> account.authenticate("wrong", 1_000_000L))
                    .isInstanceOf(BizException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.BIZ_REJECT);
        }
        repository.save(account);

        Integer failCount = jdbc.queryForObject(
                "SELECT fail_count FROM sys_staff WHERE id = 9001", Integer.class);
        assertThat(failCount).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT status FROM sys_staff WHERE id = 9001", String.class))
                .isEqualTo(UserAccount.LOCKED);
        UserAccount reloaded = repository.findByUsername("admin").orElseThrow();
        assertThat(reloaded.isLocked(1_000_000L)).isTrue();
    }

    @Test
    @DisplayName("客户按手机号直查 customer 表，重启后仍可登录（不读内存）")
    void customerAccountIsDatabaseBacked() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, status, fail_count, token_version, deleted) "
                        + "VALUES (9002, 'CUS-9002', '13800138000', '123456', '用户8000', 'PERSON', "
                        + "'LV1', 'ACTIVE', 0, 0, 0)");

        UserAccount account = repository.findByPhone("13800138000").orElseThrow();
        assertThat(account.getUserId()).isEqualTo(9002L);
        assertThat(account.getAud()).isEqualTo(UserAccount.AUD_USER);
        account.authenticate("123456", System.currentTimeMillis());
        assertThat(repository.findById("user", 9002L)).isPresent();
    }

    @Test
    @DisplayName("企业账号读 customer.type 与 enterprise_profile.status，供登录审核门禁使用")
    void enterpriseAuditStatusIsLoadedFromDatabase() {
        jdbc.update("INSERT INTO customer (id, customer_no, phone, password_hash, nickname, type, "
                        + "level_code, status, fail_count, token_version, deleted) "
                        + "VALUES (9100, 'CUS-9100', '13912345678', '123456', '企业用户5678', 'ENTERPRISE', "
                        + "'LV1', 'ACTIVE', 0, 0, 0)");
        jdbc.update("INSERT INTO enterprise_profile (customer_id, company_name, license_no, license_url, status) "
                        + "VALUES (9100, '森特测试有限公司', '91310000MA1FL0AB1X', 'lic-file-1', 'PENDING')");

        UserAccount account = repository.findByPhone("13912345678").orElseThrow();
        assertThat(account.getCustomerType()).isEqualTo("ENTERPRISE");
        assertThat(account.getEnterpriseStatus()).isEqualTo("PENDING");
        assertThat(repository.findById("user", 9100L).orElseThrow().getEnterpriseStatus())
                .isEqualTo("PENDING");
    }
}
