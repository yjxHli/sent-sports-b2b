package com.sent.sports.file;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.storage.Storage;
import com.sent.sports.file.controller.EnterpriseLicenseUploadController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

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

    private final Storage storage = mock(Storage.class);
    private final EnterpriseLicenseUploadController controller =
            new EnterpriseLicenseUploadController(storage);

    @Test
    @DisplayName("PNG 营业执照：落盘并返回 fileId")
    void pngLicenseAccepted() throws Exception {
        when(storage.store(any(byte[].class), anyString())).thenReturn("2026/11/ab/abc.png");
        byte[] image = sharpPng();
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.png", "image/png", image);

        var vo = controller.upload(file).getData();

        assertThat(vo.fileId()).isEqualTo("2026/11/ab/abc.png");
        assertThat(vo.url()).isEqualTo("/api/files/2026/11/ab/abc.png");
        assertThat(vo.size()).isEqualTo((long) image.length);
    }

    @Test
    @DisplayName("浏览器把 JPG 传成 octet-stream 也要放行（按扩展名兜底）")
    void jpgWithOctetStreamAccepted() throws Exception {
        when(storage.store(any(byte[].class), anyString())).thenReturn("2026/11/ab/x.jpg");
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.JPG", "application/octet-stream", sharpPng());

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

    @Test
    @DisplayName("纯色糊图直接拒绝，不落盘")
    void blurryLicenseRejected() {
        BufferedImage image = new BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(250, 250, 250));
        g.fillRect(0, 0, 640, 420);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        MockMultipartFile blurry = new MockMultipartFile(
                "file", "blurry.png", "image/png", out.toByteArray());

        assertThatThrownBy(() -> controller.upload(blurry))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).resolvedMessage())
                .asString().contains("模糊");
        verify(storage, never()).store(any(byte[].class), anyString());
    }

    /** 带密集文字/表格线的执照样图，能通过清晰度门禁。 */
    private static byte[] sharpPng() {
        try {
            BufferedImage image = new BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 640, 420);
            g.setColor(Color.BLACK);
            for (int y = 10; y < 420; y += 16) {
                g.fillRect(8, y, 624, 2);
            }
            for (int x = 12; x < 640; x += 24) {
                g.fillRect(x, 8, 2, 404);
            }
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
