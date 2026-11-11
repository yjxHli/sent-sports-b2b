package com.sent.sports.common.mybatis;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据范围 SQL 追加器（P5-28）：把 {@code alias.column = ? / IN (...)} 注入到查询最外层 WHERE。
 * <p>不引入 SQL 解析库，用一次轻量词法扫描实现：
 * <ul>
 *   <li>跳过单引号/双引号/反引号字符串与 {@code --}、{@code /*} 注释，避免把字面量里的 where 当成关键字；</li>
 *   <li>只在括号深度 0 识别关键字，子查询内的 WHERE 不受影响；</li>
 *   <li>已有 WHERE → {@code WHERE (<范围>) AND (<原条件>)}，括号保证 OR 优先级不被破坏；</li>
 *   <li>无 WHERE → 在 GROUP BY / HAVING / ORDER BY / LIMIT 之前（或末尾）插入 {@code WHERE <范围>}。</li>
 * </ul>
 * UNION 查询暂不支持，直接拒绝（安全默认：宁可报错，也不能漏过滤造成越权）。
 */
public final class DataScopeSqlAppender {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private static final Set<String> TAIL_KEYWORDS = Set.of("GROUP", "HAVING", "ORDER", "LIMIT", "UNION");

    private DataScopeSqlAppender() {
    }

    /** 校验表别名/列名，杜绝注解值带入 SQL 注入。 */
    public static String requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return value;
    }

    /** 生成带别名的列限定名，alias 为空时只返回列名。 */
    public static String qualified(String alias, String column) {
        String safeColumn = requireIdentifier(column);
        if (alias == null || alias.isBlank()) {
            return safeColumn;
        }
        return requireIdentifier(alias) + "." + safeColumn;
    }

    /**
     * 把完整条件（如 {@code o.owner_staff_id IN (1,2)}）注入 SQL。
     * 非 SELECT/WITH 语句、空条件、ALL 范围（调用方不调用本方法）均原样返回。
     */
    public static String append(String sql, String condition) {
        if (sql == null || sql.isBlank() || condition == null || condition.isBlank()) {
            return sql;
        }
        String text = sql.trim();
        if (!startsWithSelect(text)) {
            return sql; // 本期只约束查询（列表/导出），写入由业务状态机与权限码控制
        }
        Scan scan = scan(text);
        if (scan.unionIndex >= 0) {
            throw new BizException(ErrorCode.BIZ_REJECT);
        }
        if (scan.whereIndex >= 0) {
            int bodyStart = scan.whereIndex + "WHERE".length();
            int bodyEnd = scan.tailIndex >= 0 ? scan.tailIndex : text.length();
            String originalWhere = text.substring(bodyStart, bodyEnd).trim();
            String suffix = scan.tailIndex >= 0 ? " " + text.substring(scan.tailIndex).trim() : "";
            return text.substring(0, bodyStart)
                    + " (" + condition + ") AND (" + originalWhere + ")"
                    + suffix;
        }
        int insertAt = scan.tailIndex >= 0 ? scan.tailIndex : text.length();
        String head = stripTrailing(text.substring(0, insertAt));
        String tail = insertAt < text.length() ? text.substring(insertAt).trim() : "";
        return head + " WHERE " + condition + (tail.isEmpty() ? "" : " " + tail);
    }

    private static boolean startsWithSelect(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    private static String stripTrailing(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static Scan scan(String sql) {
        int depth = 0;
        int whereIndex = -1;
        int tailIndex = -1;
        int unionIndex = -1;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = skipQuoted(sql, i, c);
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i = skipLineComment(sql, i + 2);
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(sql, i + 2);
                continue;
            }
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }
            if (depth == 0 && isWordChar(c) && !Character.isDigit(c)) {
                int end = i + 1;
                while (end < sql.length() && isWordChar(sql.charAt(end))) {
                    end++;
                }
                String word = sql.substring(i, end).toUpperCase(Locale.ROOT);
                if (whereIndex < 0 && "WHERE".equals(word)) {
                    whereIndex = i;
                } else if ("UNION".equals(word)) {
                    if (unionIndex < 0) {
                        unionIndex = i;
                    }
                } else if (tailIndex < 0 && TAIL_KEYWORDS.contains(word)) {
                    tailIndex = i;
                }
                i = end;
                continue;
            }
            i++;
        }
        return new Scan(whereIndex, tailIndex, unionIndex);
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\' && i + 1 < sql.length()) {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2; // 转义的重复引号
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static int skipLineComment(String sql, int start) {
        int i = start;
        while (i < sql.length() && sql.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private static int skipBlockComment(String sql, int start) {
        int i = sql.indexOf("*/", start);
        return i < 0 ? sql.length() : i + 2;
    }

    private static boolean isWordChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    private record Scan(int whereIndex, int tailIndex, int unionIndex) {
    }
}
