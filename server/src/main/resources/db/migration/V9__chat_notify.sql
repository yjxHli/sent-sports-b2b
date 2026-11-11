-- V9 客服与通知/文件（9 表；不建 job_lock）
CREATE TABLE chat_session (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id  BIGINT UNSIGNED NOT NULL,
    staff_id     BIGINT UNSIGNED NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'WAITING',
    source_type  VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    source_id    VARCHAR(64)  NULL,
    vip          TINYINT      NOT NULL DEFAULT 0,
    priority     INT          NOT NULL DEFAULT 0,
    seq          BIGINT       NOT NULL DEFAULT 0,
    started_at   DATETIME(3)  NULL,
    ended_at     DATETIME(3)  NULL,
    last_msg_at  DATETIME(3)  NULL,
    close_reason VARCHAR(32)  NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_session_customer (customer_id, status),
    KEY idx_session_staff (staff_id, status),
    KEY idx_session_queue (status, vip, priority, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服会话（seq 为消息序号分配器）';

CREATE TABLE chat_message (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_no    VARCHAR(48)  NULL,
    session_id    BIGINT UNSIGNED NOT NULL,
    seq           BIGINT       NOT NULL,
    sender_type   VARCHAR(16)  NOT NULL,
    sender_id     BIGINT UNSIGNED NULL,
    msg_type      VARCHAR(24)  NOT NULL DEFAULT 'TEXT',
    content       TEXT         NULL,
    payload_json  JSON         NULL,
    client_msg_id VARCHAR(64)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_no (message_no),
    UNIQUE KEY uk_msg_seq (session_id, seq),
    UNIQUE KEY uk_msg_client (session_id, client_msg_id),
    KEY idx_msg_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服消息';

CREATE TABLE chat_read_cursor (
    session_id    BIGINT UNSIGNED NOT NULL,
    side          VARCHAR(16)  NOT NULL,
    last_read_seq BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NULL,
    PRIMARY KEY (session_id, side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已读游标';

CREATE TABLE staff_presence (
    staff_id          BIGINT UNSIGNED NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE',
    max_sessions      INT          NOT NULL DEFAULT 10,
    active_sessions   INT          NOT NULL DEFAULT 0,
    last_heartbeat_at DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT       NULL,
    PRIMARY KEY (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='坐席在线状态';

CREATE TABLE chat_quick_reply (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_id   BIGINT UNSIGNED NULL,
    scope      VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL',
    content    VARCHAR(1024) NOT NULL,
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_quick_scope (scope, staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快捷回复';

CREATE TABLE chat_session_tag (
    session_id BIGINT UNSIGNED NOT NULL,
    tag        VARCHAR(32)  NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (session_id, tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话标签';

CREATE TABLE notification (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    title       VARCHAR(128) NOT NULL,
    content     VARCHAR(1024) NULL,
    biz_type    VARCHAR(32)  NULL,
    biz_id      VARCHAR(64)  NULL,
    read_flag   TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_notify_customer (customer_id, read_flag, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信';

CREATE TABLE notify_template (
    code        VARCHAR(64)  NOT NULL,
    channel     VARCHAR(16)  NOT NULL DEFAULT 'INBOX',
    title_tpl   VARCHAR(255) NULL,
    content_tpl TEXT         NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知模板';

CREATE TABLE file_object (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_type   VARCHAR(24)  NOT NULL,
    file_name  VARCHAR(255) NOT NULL,
    path       VARCHAR(512) NOT NULL,
    size       BIGINT       NOT NULL,
    sha256     CHAR(64)     NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_sha (sha256, size)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件对象';
