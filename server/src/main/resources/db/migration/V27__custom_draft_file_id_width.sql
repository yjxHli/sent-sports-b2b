-- 定制图稿 file_id 放宽到 VARCHAR(255)。
-- 背景：V13 按「字符串形式的文件服务 ID」把 BIGINT 改成 VARCHAR(64)，但文件服务返回的是
-- 内容寻址的相对路径（形如 2026/11/79/<64 位 sha256>.png，共 79 字符），VARCHAR(64) 装不下，
-- 用户上传真实图稿时会直接 "Data too long for column 'file_id'"。
ALTER TABLE custom_draft
    MODIFY COLUMN file_id VARCHAR(255) NULL COMMENT '文件服务相对路径（fileId）';
