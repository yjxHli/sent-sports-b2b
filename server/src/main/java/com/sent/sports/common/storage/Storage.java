package com.sent.sports.common.storage;

/**
 * 文件存储端口（R8 / 适配器模式）：业务侧只依赖本接口，不依赖任何落盘实现。
 *
 * <p>方法返回的是「相对路径 fileId」，语义由实现保证：调用方把它原样存进业务表，
 * 再交给 {@code GET /api/files/{fileId}} 读取即可。切换本地磁盘 / OSS / 内存实现时
 * 只需替换 Spring Bean，上传端点与读端点都不改代码。</p>
 */
public interface Storage {

    /**
     * 写入文件，返回可供后续 {@link #load(String)} 的相对路径（fileId）。
     * 同内容重复写入应幂等：返回同一路径且磁盘不膨胀。
     *
     * @throws IllegalArgumentException 内容为空等参数错误
     */
    String store(byte[] content, String originalName);

    /** 读取文件；不存在返回 {@code null}；路径越界抛 {@link IllegalArgumentException}。 */
    byte[] load(String fileId);

    /** 删除文件；不存在视为成功并返回 {@code false}（便于重试）。 */
    boolean delete(String fileId);
}
