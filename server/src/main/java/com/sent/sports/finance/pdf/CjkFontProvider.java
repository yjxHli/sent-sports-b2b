package com.sent.sports.finance.pdf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 中文字体定位：发票必须内嵌中文字体子集，否则票面全是空框。
 * <p>优先用配置 {@code app.invoice.font-file}；未配置时按各系统常见路径探测。
 * 只接受单个 {@code .ttf / .otf}——{@code .ttc} 字体集合 PDFBox 需要额外的
 * TrueTypeCollection 流程，为避免运行期隐患，这里直接跳过。</p>
 */
@Component
public class CjkFontProvider {

    /** 探测顺序：Windows → Linux → macOS。 */
    static final List<String> CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simkai.ttf",
            "C:/Windows/Fonts/simfang.ttf",
            "C:/Windows/Fonts/msyh.ttf",
            "C:/Windows/Fonts/Deng.ttf",
            "/usr/share/fonts/truetype/arphic/uming.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf",
            "/System/Library/Fonts/Supplemental/Songti.ttc");

    private final String configuredPath;

    public CjkFontProvider(@Value("${app.invoice.font-file:}") String configuredPath) {
        this.configuredPath = configuredPath == null ? "" : configuredPath.trim();
    }

    /** Spring 注入的配置路径优先。 */
    public File resolve() {
        return resolve(configuredPath);
    }

    /**
     * 按「显式配置 → 候选路径」返回第一个可用的中文字体文件；都没有则返回 {@code null}
     * （由渲染器抛出可定位的错误，而不是生成一堆空框）。
     */
    public static File resolve(String configuredPath) {
        List<String> paths = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            paths.add(configuredPath.trim());
        }
        paths.addAll(CANDIDATES);
        for (String path : paths) {
            if (!isEmbeddable(path)) {
                continue;
            }
            File file = new File(path);
            if (file.isFile() && file.length() > 0) {
                return file;
            }
        }
        return null;
    }

    private static boolean isEmbeddable(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }
}
