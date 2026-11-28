package com.sent.sports.system.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Optional;

/**
 * 业务参数字典（系统配置中心，2026-11-25）：把 {@code sys_config} 的极客键名翻译成运营读得懂的话。
 *
 * <p>为什么用枚举而不是给 {@code sys_config} 加两列中文名：</p>
 * <ul>
 *   <li>中文名与「这个值怎么用」必须跟代码一起走版本——参数在代码里被读取的地方（订单超时任务、
 *       库存预警报表）与它的说明天然同源，分两处维护迟早对不上；</li>
 *   <li>取值类型同时是<b>写入口径</b>：没有类型，运营把「30 分钟」填成「半小时」也能存进库，
 *       而读取方 {@code Integer.parseInt} 会在定时任务里静默抛错，表现为「订单再也不自动关闭」。</li>
 * </ul>
 *
 * <p>库里的键可能不止这里列出的（历史遗留、临时排查手工加的），所以查找必须返回
 * {@link Optional} 而不是抛错：未知键按自由文本展示，页面不能因为一条陌生配置整页 500。</p>
 */
public enum SystemConfigCatalog {

    /* ---------- 订单与交易 ---------- */

    ORDER_PAY_TIMEOUT_MINUTES("order.pay.timeout.minutes", "订单支付超时时间",
            "订单与交易", "分钟", ConfigValueType.NUMBER,
            "用户下单后未支付，系统自动取消订单的期限。填 0 表示不自动取消（演示环境排障用）。"),
    ORDER_AUTO_CONFIRM_DAYS("order.auto.confirm.days", "发货后自动确认收货",
            "订单与交易", "天", ConfigValueType.NUMBER,
            "发货后多少天客户没有点「确认收货」，系统自动确认，货款随之进入可结算状态。"),
    ORDER_CLOSE_AFTER_DAYS("order.close.after.days", "售后申请期限",
            "订单与交易", "天", ConfigValueType.NUMBER,
            "订单完成后多少天内仍可申请售后（退货 / 换货 / 维修），超过则只能走人工特批。"),

    /* ---------- 库存与商品 ---------- */

    STOCK_WARNING_THRESHOLD("stock.warning.threshold", "库存安全预警阈值",
            "库存与商品", "件", ConfigValueType.NUMBER,
            "商品可用库存低于此值时，库存预警报表与首页指标卡会标红提醒补货。"),

    /* ---------- 财务与开票 ---------- */

    INVOICE_TAX_RATE("finance.invoice.tax.rate", "发票税率",
            "财务与开票", "%", ConfigValueType.PERCENT,
            "开具增值税发票时使用的税率（0~100 的整数，如 13 表示 13%）。"),
    CREDIT_DEFAULT_LIMIT_CENT("finance.credit.default.limit.cent", "企业客户默认授信额度",
            "财务与开票", "分", ConfigValueType.MONEY_CENT,
            "企业最高可申请授信额度，单位是「分」：200000 分 = 2000.00 元。"),
    CREDIT_DEFAULT_TERM_DAYS("finance.credit.default.term.days", "企业客户默认账期",
            "财务与开票", "天", ConfigValueType.NUMBER,
            "企业客户赊账后多少天内应回款，超期会在应收账龄报表里标记逾期。"),

    /* ---------- 会员与积分 ---------- */

    MEMBER_POINT_RATIO("member.point.exchange.ratio", "积分兑换比例",
            "会员与积分", "积分", ConfigValueType.NUMBER,
            "多少积分可抵扣 1 元，如 100 表示 100 积分 = 1 元。"),
    MEMBER_LEVEL_GROWTH("member.level.upgrade.growth", "会员升级成长值门槛",
            "会员与积分", "成长值", ConfigValueType.NUMBER,
            "每提升一个会员等级需要的成长值，成长值按已支付订单金额累计。");

    /** 配置键长度上限，与 {@code sys_config.config_key} 列宽一致。 */
    private static final int KEY_MAX = 96;
    /** 配置值长度上限，与 {@code sys_config.config_value} 列宽一致：超了会在 SQL 层报错。 */
    private static final int VALUE_MAX = 1024;

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");

    private final String key;
    private final String displayName;
    private final String groupName;
    private final String unit;
    private final ConfigValueType type;
    private final String description;

    SystemConfigCatalog(String key, String displayName, String groupName, String unit,
                        ConfigValueType type, String description) {
        this.key = key;
        this.displayName = displayName;
        this.groupName = groupName;
        this.unit = unit;
        this.type = type;
        this.description = description;
    }

    /** 配置键（{@code sys_config.config_key}）。 */
    public String key() {
        return key;
    }

    /** 中文名：列表主标题、报错文案都用它。 */
    public String displayName() {
        return displayName;
    }

    /** 业务分组：列表按组排序，运营先看到「订单」再看到「会员」。 */
    public String groupName() {
        return groupName;
    }

    /** 单位（分钟 / 天 / 件 / % / 分 / 积分 / 成长值）。 */
    public String unit() {
        return unit;
    }

    public ConfigValueType type() {
        return type;
    }

    /** 一句话说明「改这个值会发生什么」，编辑弹窗必须展示。 */
    public String description() {
        return description;
    }

    /**
     * 按键查字典。
     *
     * @return 未知键 / 空白键返回 {@link Optional#empty()}，由调用方按自由文本处理
     */
    public static Optional<SystemConfigCatalog> of(String key) {
        String target = key == null ? "" : key.trim();
        if (target.isEmpty() || target.length() > KEY_MAX) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(item -> item.key.equals(target)).findFirst();
    }

    /**
     * 写入口径：按类型校验并归一（去空格 / 布尔小写 / 数字去前导零）。
     *
     * @throws BizException 取值不符合本项类型，文案里带中文名
     */
    public String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw invalid("不能为空");
        }
        if (value.length() > VALUE_MAX) {
            throw invalid("最长 " + VALUE_MAX + " 个字符");
        }
        return switch (type) {
            case NUMBER, MONEY_CENT -> digits(value, null);
            case PERCENT -> digits(value, 100);
            case BOOLEAN -> bool(value);
            case TEXT -> value;
        };
    }

    /**
     * 展示值：数字带单位、金额翻成元、布尔翻成开 / 关。
     * <p>脏数据（库里存了字母）原样返回，不抛错——报表能看出异常比整页打不开有用。</p>
     */
    public String display(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "未设置";
        }
        if (type == ConfigValueType.BOOLEAN) {
            return "true".equalsIgnoreCase(value) ? "开启" : "关闭";
        }
        if (type == ConfigValueType.MONEY_CENT) {
            try {
                return "¥" + MONEY_FORMAT.format(new BigDecimal(value).movePointLeft(2));
            } catch (NumberFormatException e) {
                return value;
            }
        }
        if (type == ConfigValueType.TEXT) {
            return value;
        }
        return unit.isEmpty() ? value : value + " " + unit;
    }

    /** 非负整数；{@code max} 非空时再校验上限（百分比专用）。 */
    private String digits(String value, Integer max) {
        if (!value.chars().allMatch(Character::isDigit)) {
            throw invalid("只能填非负整数" + (unit.isEmpty() ? "" : "（单位：" + unit + "）"));
        }
        // 去掉前导零：运营填 030 与 30 是同一个意思，落库统一成 30，避免「值不同其实相等」
        String normalized = value.replaceFirst("^0+(?=\\d)", "");
        // 位数上限：超长数字转 int 会抛 NumberFormatException，变成 50001「系统异常」
        if (normalized.length() > 9) {
            throw invalid("数值过大，请填写 9 位以内的整数");
        }
        if (max != null && Integer.parseInt(normalized) > max) {
            throw invalid("不能大于 " + max);
        }
        return normalized;
    }

    private String bool(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "false";
        }
        throw invalid("只能是 true 或 false");
    }

    private BizException invalid(String why) {
        return new BizException(ErrorCode.PARAM_INVALID, "「" + displayName + "」" + why);
    }
}
