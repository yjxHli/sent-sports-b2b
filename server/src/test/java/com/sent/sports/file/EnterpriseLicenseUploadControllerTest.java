package com.sent.sports.file;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.storage.LocalFileStorage;
import com.sent.sports.file.controller.EnterpriseLicenseUploadController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业注册营业执照上传（匿名端点）：
 * 只放行 JPG/PNG 且 ≤ 2MB，其他类型一律拒绝 —— 匿名上传口子必须窄。
 */
class EnterpriseLicenseUploadControllerTest {

    private final LocalFileStorage storage = mock(LocalFileStorage.class);
    private final EnterpriseLicenseUploadController controller =
            new EnterpriseLicenseUploadController(storage);

    @Test
    @DisplayName("PNG 营业执照：落盘并返回 fileId")
    void pngLicenseAccepted() throws Exception {
        when(storage.store(any(byte[].class), anyString())).thenReturn("2026/11/ab/abc.png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.png", "image/png", new byte[]{1, 2, 3});

        var vo = controller.upload(file).getData();

        assertThat(vo.fileId()).isEqualTo("2026/11/ab/abc.png");
        assertThat(vo.url()).isEqualTo("/api/files/2026/11/ab/abc.png");
        assertThat(vo.size()).isEqualTo(3L);
    }

    @Test
    @DisplayName("浏览器把 JPG 传成 octet-stream 也要放行（按扩展名兜底）")
    void jpgWithOctetStreamAccepted() throws Exception {
        when(storage.store(any(byte[].class), anyString())).thenReturn("2026/11/ab/x.jpg");
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.JPG", "application/octet-stream", new byte[]{9});

        assertThat(controller.upload(file).getData().fileId()).isEqualTo("2026/11/ab/x.jpg");
    }

    @Test
    @DisplayName("PDF / 可执行文件一律拒绝，绝不落盘")
    void nonImageRejected() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> controller.upload(pdf))
                .isInstanceOf(BizException.class)
                // BizException.getMessage() 是默认文案，业务原因在 resolvedMessage()
                .extracting(e -> ((BizException) e).resolvedMessage())
                .asString()
                .contains("仅支持 JPG / PNG");
        verify(storage, never()).store(any(byte[].class), anyString());
    }

    @Test
    @DisplayName("伪装成 image/png 的 .exe 也要拒绝")
    void disguisedExecutableRejected() {
        MockMultipartFile exe = new MockMultipartFile(
                "file", "virus.exe", "image/png", new byte[]{1});

        assertThatThrownBy(() -> controller.upload(exe))
                .isInstanceOf(BizException.class);
        verify(storage, never()).store(any(byte[].class), anyString());
    }

    @Test
    @DisplayName("超过 2MB 拒绝，避免匿名上传撑爆磁盘")
    void oversizeRejected() {
        byte[] big = new byte[(int) (2L * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", big);

        assertThatThrownBy(() -> controller.upload(file))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).resolvedMessage())
                .asString()
                .contains("2MB");
        verify(storage, never()).store(any(byte[].class), anyString());
    }

    @Test
    @DisplayName("空文件按参数错误处理")
    void emptyFileRejected() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> controller.upload(empty))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }
}
