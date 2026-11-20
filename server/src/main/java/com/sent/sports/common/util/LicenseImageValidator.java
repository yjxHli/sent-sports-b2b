package com.sent.sports.common.util;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * 营业执照图片质量门禁：只依赖 JDK ImageIO，不引入图像识别依赖。
 *
 * <p>校验两件事：① 尺寸太小看不清企业名称与信用代码；② 拉普拉斯方差过低＝失焦/纯色图。
 * 阈值特意取宽松值，只挡「明显糊成一片」的图片，避免误伤手机拍摄的正常执照。</p>
 */
public final class LicenseImageValidator {

    private static final int MIN_WIDTH = 320;
    private static final int MIN_HEIGHT = 240;
    private static final int SAMPLE = 100;
    /** 拉普拉斯方差阈值：纯色/严重失焦接近 0，带文字表格的执照远大于此值。 */
    private static final double MIN_SHARPNESS = 80.0;

    private LicenseImageValidator() {
    }

    public static void validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传营业执照");
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业执照图片无法识别，请上传 JPG / PNG");
        }
        if (source == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业执照图片无法识别，请上传 JPG / PNG");
        }
        if (source.getWidth() < MIN_WIDTH || source.getHeight() < MIN_HEIGHT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业执照图片尺寸过小，请上传清晰照片");
        }
        if (sharpness(source) < MIN_SHARPNESS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业执照图片模糊，请重新上传清晰照片");
        }
    }

    /** 缩小到 100×100 后算灰度拉普拉斯方差，兼顾速度与抗锯齿噪声。 */
    private static double sharpness(BufferedImage source) {
        BufferedImage small = new BufferedImage(SAMPLE, SAMPLE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, SAMPLE, SAMPLE, null);
        } finally {
            g.dispose();
        }
        int[] gray = new int[SAMPLE * SAMPLE];
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                int rgb = small.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y * SAMPLE + x] = (int) (0.299 * r + 0.587 * gg + 0.114 * b);
            }
        }
        long sum = 0;
        long sumSq = 0;
        int count = 0;
        for (int y = 1; y < SAMPLE - 1; y++) {
            for (int x = 1; x < SAMPLE - 1; x++) {
                int i = y * SAMPLE + x;
                int lap = 4 * gray[i] - gray[i - 1] - gray[i + 1]
                        - gray[i - SAMPLE] - gray[i + SAMPLE];
                sum += lap;
                sumSq += (long) lap * lap;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        double mean = (double) sum / count;
        return (double) sumSq / count - mean * mean;
    }
}
