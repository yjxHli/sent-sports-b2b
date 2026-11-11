package com.sent.sports.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 本地文件存储单测（P5-32）：固定时钟与临时目录，验证内容去重、扩展名清洗与路径穿越防护。 */
class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        storage = new LocalFileStorage(tempDir.toString(), fixed);
    }

    @Test
    @DisplayName("按年月分目录落盘，文件名取 SHA-256")
    void storeWritesUnderYearMonthByDigest() {
        byte[] content = "球衣定制图稿".getBytes(StandardCharsets.UTF_8);

        String relative = storage.store(content, "draft.PNG");

        assertThat(relative).matches("2026/11/[0-9a-f]{2}/[0-9a-f]{64}\\.png");
        assertThat(Files.exists(tempDir.resolve(relative))).isTrue();
        assertThat(storage.read(relative)).isEqualTo(content);
    }

    @Test
    @DisplayName("同内容重复上传只存一份（幂等写入，返回同一路径）")
    void sameContentIsStoredOnce() throws IOException {
        byte[] content = "same-bytes".getBytes(StandardCharsets.UTF_8);

        String first = storage.store(content, "a.jpg");
        String second = storage.store(content, "b.jpg");

        assertThat(second).isEqualTo(first);
        try (var stream = Files.walk(tempDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            assertThat(files).hasSize(1);
        }
    }

    @Test
    @DisplayName("不同内容落到不同路径")
    void differentContentGoesToDifferentPaths() {
        String a = storage.store("a".getBytes(StandardCharsets.UTF_8), "x.png");
        String b = storage.store("b".getBytes(StandardCharsets.UTF_8), "x.png");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("扩展名清洗：大写转小写，含非法字符则丢弃")
    void extensionIsSanitised() {
        String upper = storage.store("u".getBytes(StandardCharsets.UTF_8), "photo.JPEG");
        String weird = storage.store("w".getBytes(StandardCharsets.UTF_8), "noext.we!rd");
        String none = storage.store("n".getBytes(StandardCharsets.UTF_8), "noext");

        assertThat(upper).endsWith(".jpeg");
        assertThat(weird).doesNotContain("!");
        assertThat(none).matches(".*[0-9a-f]{64}");
    }

    @Test
    @DisplayName("路径穿越被拒绝")
    void traversalIsRejected() {
        assertThatThrownBy(() -> storage.resolve("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(new byte[0], "a.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("删除幂等，读取不存在的文件返回 null")
    void deleteIsIdempotent() {
        String relative = storage.store("gone".getBytes(StandardCharsets.UTF_8), "a.txt");

        assertThat(storage.delete(relative)).isTrue();
        assertThat(storage.delete(relative)).isFalse();
        assertThat(storage.read(relative)).isNull();
    }
}
