package com.sent.sports.customer.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 客户标签分类（2026-11-25）：客服之间共享的「这个客户是什么人」判断，分四档语义。
 *
 * <p>为什么要分类而不是一张平表：标签是要在会话头部一眼扫过去的，</p>
 * <ul>
 *   <li>「忠实老客」和「羊毛党」在视觉上必须能立刻区分（颜色），否则客服会把风险客户当贵客接待；</li>
 *   <li>颜色不写死在前端：{@link #colorKey()} 由后端下发，管理端与客服端、以后的 App 才能一致；</li>
 *   <li>未知分类回落中性档：脏数据只是颜色变灰，不能让整页 500。</li>
 * </ul>
 *
 * <p>这些标签只有客服与后台人员可见，<b>用户端任何接口都不得返回</b>——
 * 让客户看到自己被标成「羊毛党」是重大事故。</p>
 */
public enum TagCategory {

    /** 优质 / 正面：值得优先接待、可以给权益的客户。 */
    EXCELLENT("优质 / 正面", "excellent"),
    /** 中性普通：正常客户，没有特别判断。 */
    NEUTRAL("中性普通", "neutral"),
    /** 风险留意：需要谨慎承诺、留证据的客户。 */
    RISK("风险留意", "risk"),
    /** 恶意：羊毛党 / 差评勒索等，按公司规则处理。 */
    MALICIOUS("恶意", "malicious");

    private final String label;
    private final String colorKey;

    TagCategory(String label, String colorKey) {
        this.label = label;
        this.colorKey = colorKey;
    }

    /** 中文名（弹窗分组标题、列表标签）。 */
    public String label() {
        return label;
    }

    /** 配色键：前端映射到设计令牌，不直接下发色值（浅色/深色主题各端自己决定具体色号）。 */
    public String colorKey() {
        return colorKey;
    }

    /** 读库口径：null / 空 / 未知值一律按中性处理。 */
    public static TagCategory of(String raw) {
        String value = raw == null ? "" : raw.trim();
        for (TagCategory item : values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        return NEUTRAL;
    }

    /** 写入口径：只认白名单，别猜运营想表达什么。 */
    public static TagCategory require(String raw) {
        String value = raw == null ? "" : raw.trim();
        for (TagCategory item : values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "标签分类只能是 EXCELLENT / NEUTRAL / RISK / MALICIOUS");
    }
}
