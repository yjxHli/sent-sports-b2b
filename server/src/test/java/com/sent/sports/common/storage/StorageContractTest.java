package com.sent.sports.common.storage;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.file.controller.EnterpriseLicenseUploadController;
import com.sent.sports.file.controller.FileController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R8（适配器模式）契约测试：调用方只认 {@link Storage} 接口，不认具体落盘实现。
 *
 * <p>红用例先行：接口尚未抽出时本类编译失败。绿了以后，切换 OSS / 内存实现
 * 只需替换 Bean，两个上传端点与文件读取端点都无需改动。</p>
 */
class StorageContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("LocalFileStorage 实现 Storage：store/load/delete 走接口引用可用")
    void localFileStorageHonoursInterface() {
        Storage storage = new LocalFileStorage(tempDir.toString(), Clock.systemUTC());

        String fileId = storage.store("契约测试".getBytes(StandardCharsets.UTF_8), "contract.txt");

        assertThat(storage.load(fileId)).isEqualTo("契约测试".getBytes(StandardCharsets.UTF_8));
        assertThat(storage.delete(fileId)).isTrue();
        assertThat(storage.load(fileId)).isNull();
    }

    @Test
    @DisplayName("FileController 注入 Storage 接口：上传与读取都经接口委派")
    void fileControllerDependsOnInterface() throws Exception {
        Storage storage = mock(Storage.class);
        when(storage.store(any(byte[].class), anyString())).thenReturn("2026/11/ab/abc.png");
        when(storage.load("2026/11/ab/abc.png")).thenReturn("image-bytes".getBytes(StandardCharsets.UTF_8));
        FileController controller = new FileController(storage);
        CurrentUser user = new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti-1");
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "image-bytes".getBytes(StandardCharsets.UTF_8));

        var vo = controller.upload(file, user).getData();

        assertThat(vo.fileId()).isEqualTo("2026/11/ab/abc.png");
        verify(storage).store(any(byte[].class), anyString());
        assertThat(controller.read("2026/11/ab/abc.png").getBody()).isNotNull();
        verify(storage).load("2026/11/ab/abc.png");
    }

    @Test
    @DisplayName("企业执照上传端点同样只依赖 Storage 接口")
    void licenseControllerDependsOnInterface() throws Exception {
        Storage storage = mock(Storage.class);
        when(storage.store(any(byte[].class), anyString())).thenReturn("2026/11/ab/license.png");
        EnterpriseLicenseUploadController controller = new EnterpriseLicenseUploadController(storage);

        assertThat(controller).isNotNull();
    }
}
