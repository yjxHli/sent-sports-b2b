package com.sent.sports.auth.config;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.UserAccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 验收种子账号（开发 / 测试环境）：把 V11 / V13 落到 DB 的三个账号同步进进程内账号仓储。
 *
 * <p>{@code InMemoryUserAccountRepository} 是登录用例唯一的数据源，Flyway 只写 DB 并不会让它
 * 「查得到」；因此本 Runner 在 dev / test profile（{@code app.seed-accounts.enabled=true}）下
 * 补上内存侧账号。userId 与 V11 的 {@code sys_staff.id} / {@code customer.id} 严格对齐，
 * 保证 {@code /api/admin/me}、{@code /api/me}、数据范围拦截器拿到同一个主体。</p>
 *
 * <p>密码沿用 {@link UserAccount} 的明文占位口径（P3 设计），生产环境接 BCrypt 时只需替换
 * 该仓储实现，本 Runner 无需改动。</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed-accounts.enabled", havingValue = "true")
public class AccountSeeder implements ApplicationRunner {

    public static final long ADMIN_USER_ID = 9001L;
    public static final long CUSTOMER_USER_ID = 9002L;
    /** 用户端演示账号：手机号 13800138000 / 123456（客户以手机号作为登录标识）。 */
    public static final String DEMO_CUSTOMER_PHONE = "13800138000";
    /** V13：客服账号 service / 123456，角色仅持 custom:review，用于定制图稿审核验收。 */
    public static final long SERVICE_USER_ID = 9003L;
    /** V14：演示业务岗（userId 与 sys_staff.id 严格对齐，密码沿用明文 123456 占位口径）。 */
    public static final long SALES_MANAGER_USER_ID = 9004L;
    public static final long SALES_USER_ID = 9005L;
    public static final long FINANCE_USER_ID = 9006L;
    public static final long WAREHOUSE_USER_ID = 9007L;

    private final UserAccountRepository userAccountRepository;

    public AccountSeeder(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        userAccountRepository.save(new UserAccount(ADMIN_USER_ID, "admin", "123456"));
        // 客户账号以手机号为登录标识：username 与 phone 同为 13800138000。
        userAccountRepository.save(
                new UserAccount(CUSTOMER_USER_ID, DEMO_CUSTOMER_PHONE, DEMO_CUSTOMER_PHONE, "123456"));
        userAccountRepository.save(new UserAccount(SERVICE_USER_ID, "service", "123456"));
        // V14 演示业务岗：与 sys_staff(9004~9007) 一一对应，保证管理端六类员工均可登录验收。
        userAccountRepository.save(new UserAccount(SALES_MANAGER_USER_ID, "sales_mgr", "123456"));
        userAccountRepository.save(new UserAccount(SALES_USER_ID, "sales", "123456"));
        userAccountRepository.save(new UserAccount(FINANCE_USER_ID, "finance", "123456"));
        userAccountRepository.save(new UserAccount(WAREHOUSE_USER_ID, "warehouse", "123456"));
    }
}
