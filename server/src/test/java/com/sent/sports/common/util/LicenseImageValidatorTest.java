package com.sent.sports.common.util;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 营业执照图片质量校验（TDD 红 → 绿）。
 * <p>只靠「JPG/PNG + 2MB」挡不住一张纯色小图冒充营业执照，
 * 后端用纯 JDK ImageIO 做尺寸 + 拉普拉斯方差清晰度校验，
 * 让前端红字提示有可复用的同一口径。</p>
 */
class LicenseImageValidatorTest {

    @Test
    void nullAndGarbageRejected() {
        assertThatThrownBy(() -> LicenseImageValidator.validate(null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        assertThatThrownBy(() -> LicenseImageValidator.validate("not-an-image".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void tinyImageRejectedAsUnreadable() {
        assertThatThrownBy(() -> LicenseImageValidator.validate(png(120, 90, false)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).resolvedMessage())
                .asString().contains("尺寸过小");
    }

    @Test
    void blurryImageRejected() {
        assertThatThrownBy(() -> LicenseImageValidator.validate(png(640, 420, false)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).resolvedMessage())
                .asString().contains("模糊");
    }

    @Test
    void sharpLicenseLikeImageAccepted() {
        assertThatCode(() -> LicenseImageValidator.validate(png(640, 420, true)))
                .doesNotThrowAnyException();
    }

    /** sharp=false 生成近乎纯色的图（模拟失焦照片），sharp=true 生成密集文字/表格线条。 */
    private static byte[] png(int width, int height, boolean sharp) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            if (sharp) {
                g.setColor(Color.BLACK);
                for (int y = 10; y < height; y += 16) {
                    g.fillRect(8, y, width - 16, 2);
                }
                for (int x = 12; x < width; x += 24) {
                    g.fillRect(x, 8, 2, height - 16);
                }
            } else {
                g.setColor(new Color(248, 249, 250));
                g.fillRect(0, 0, width, height);
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
