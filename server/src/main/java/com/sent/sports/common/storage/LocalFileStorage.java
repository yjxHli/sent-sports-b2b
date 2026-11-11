package com.sent.sports.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 本地文件存储（P5-32）：按「年/月/摘要前缀」落盘，同内容只存一份（内容寻址去重）。
 *
 * <h3>设计取舍</h3>
 * <ul>
 *   <li><b>内容寻址</b>：文件名取 SHA-256，同图重复上传不会膨胀磁盘；业务侧只需保存返回的相对路径。
 *       去重是<b>幂等写入</b>（同一个键覆盖写同样的内容），不需要锁。</li>
 *   <li><b>路径安全</b>：{@link #resolve(String)} 只接受本存储器生成的相对路径，且校验最终路径仍在根目录内，
 *       杜绝 {@code ../../etc/passwd} 这类穿越读取。</li>
 *   <li><b>时间来源可注入</b>：{@link Clock} 注入使「按年月分目录」可被单测固定，不依赖真实时钟。</li>
 * </ul>
 * 说明：本期只做单机磁盘；将来切对象存储只需替换本类，调用方拿到的相对路径语义不变。
 */
public class LocalFileStorage {

    private static final int DIGEST_PREFIX_LEN = 2;
    private static final int MAX_EXT_LEN = 10;

    private final Path baseDir;
    private final Clock clock;

    public LocalFileStorage(String baseDir, Clock clock) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.clock = clock;
    }

    /**
     * 写入文件并返回相对路径（形如 {@code 2026/11/ab/abcdef...png}）。
     * 同内容重复写入是幂等操作：已存在则直接返回同一路径。
     */
    public String store(byte[] content, String originalName) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        String digest = sha256Hex(content);
        String relative = relativePath(digest, extensionOf(originalName));
        Path target = baseDir.resolve(relative).normalize();
        assertInsideBase(target);
        try {
            if (Files.exists(target)) {
                return relative;
            }
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return relative;
        } catch (IOException e) {
            throw new UncheckedIOException("写入文件失败：" + relative, e);
        }
    }

    /** 解析相对路径为绝对路径；不存在返回 empty。路径越界直接抛异常。 */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
        Path target = baseDir.resolve(relativePath).normalize();
        assertInsideBase(target);
        return target;
    }

    /** 读取文件内容（不存在返回 empty）。 */
    public byte[] read(String relativePath) {
        Path target = resolve(relativePath);
        try {
            return Files.exists(target) ? Files.readAllBytes(target) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败：" + relativePath, e);
        }
    }

    /** 删除文件（不存在视为成功，便于重试）。 */
    public boolean delete(String relativePath) {
        Path target = resolve(relativePath);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("删除文件失败：" + relativePath, e);
        }
    }

    private String relativePath(String digest, String ext) {
        LocalDate today = LocalDate.now(clock);
        return String.format("%04d/%02d/%s/%s%s",
                today.getYear(), today.getMonthValue(), digest.substring(0, DIGEST_PREFIX_LEN), digest,
                ext);
    }

    /** 只保留白名单式的短扩展名，避免把奇怪字符带进文件名。 */
    private String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > MAX_EXT_LEN || !ext.matches("[a-z0-9]+")) {
            return "";
        }
        return "." + ext;
    }

    private void assertInsideBase(Path target) {
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法路径：" + target);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}
