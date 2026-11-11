package com.sent.sports.customize.domain;

/**
 * 图稿版本实体（customize 模块，P3-25）：一次提交图稿生成一条只读版本（不 update，只 insert）。
 * 旧版本天然只读不可删，新版本由 {@code version} 递增标识。
 * 纯 Java 领域对象，无 Spring 依赖。
 */
public class CustomDraft {

    private final String draftId;
    private final String customOrderId;
    private final int version;
    private final String fileId;
    private final String fileName;
    private final long fileSize;
    private final long uploadedAtMillis;

    public CustomDraft(String draftId, String customOrderId, int version, String fileId,
                      String fileName, long fileSize, long uploadedAtMillis) {
        this.draftId = draftId;
        this.customOrderId = customOrderId;
        this.version = version;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploadedAtMillis = uploadedAtMillis;
    }

    public String getDraftId() {
        return draftId;
    }

    public String getCustomOrderId() {
        return customOrderId;
    }

    public int getVersion() {
        return version;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getUploadedAtMillis() {
        return uploadedAtMillis;
    }
}
