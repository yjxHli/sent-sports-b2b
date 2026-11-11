package com.sent.sports.customize.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 定制图稿版本 {@code custom_draft} 持久化对象（演示数据生成器写入用）。
 * <p>与 {@code custom_order} 一对多：一个定制单可挂多个图稿版本，{@code (custom_order_id, version)} 唯一。</p>
 */
@TableName("custom_draft")
public class CustomDraftPO extends BasePO {

    private Long customOrderId;
    private Integer version;
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadedAt;

    public Long getCustomOrderId() {
        return customOrderId;
    }

    public void setCustomOrderId(Long customOrderId) {
        this.customOrderId = customOrderId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
