-- V2 系统与权限（6 表）
CREATE TABLE sys_staff (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_no      VARCHAR(32)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    email         VARCHAR(128) NULL,
    password_hash VARCHAR(128) NOT NULL,
    role_id       BIGINT UNSIGNED NOT NULL,
    leader_id     BIGINT UNSIGNED NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME(3)  NULL,
    fail_count    INT          NOT NULL DEFAULT 0,
    locked_until  DATETIME(3)  NULL,
    token_version BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_no (staff_no),
    UNIQUE KEY uk_staff_phone (phone),
    KEY idx_staff_leader (leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工';

CREATE TABLE sys_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    data_scope  VARCHAR(16)  NOT NULL DEFAULT 'SELF',
    is_builtin  TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

CREATE TABLE sys_permission (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code       VARCHAR(96)  NOT NULL,
    name       VARCHAR(96)  NOT NULL,
    module     VARCHAR(48)  NOT NULL,
    type       VARCHAR(16)  NOT NULL DEFAULT 'ACTION',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点';

CREATE TABLE sys_role_permission (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限';

CREATE TABLE sys_config (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(96)  NOT NULL,
    config_value VARCHAR(1024) NULL,
    remark       VARCHAR(255) NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数';

CREATE TABLE sys_operation_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_id    BIGINT UNSIGNED NULL,
    module      VARCHAR(48)  NOT NULL,
    action      VARCHAR(48)  NOT NULL,
    target_type VARCHAR(48)  NULL,
    target_id   VARCHAR(64)  NULL,
    before_json JSON         NULL,
    after_json  JSON         NULL,
    reason      VARCHAR(255) NULL,
    ip          VARCHAR(64)  NULL,
    trace_id    VARCHAR(64)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_oplog_staff (staff_id, created_at),
    KEY idx_oplog_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';
