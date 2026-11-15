package com.sent.sports.file.dto;

/**
 * 文件服务 DTO。
 *
 * @param fileId   落盘相对路径（业务表只存这个，如 {@code 2026/11/ab/abcd....png}）
 * @param url      可直接用于 {@code <img src>} 的访问路径
 * @param fileName 原始文件名（展示用）
 * @param size     字节数
 */
public final class FileDtos {

    private FileDtos() {
    }

    public record FileVO(String fileId, String url, String fileName, long size) {
    }
}
