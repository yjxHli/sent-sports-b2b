package com.sent.sports.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词过滤器（DFA / 前缀树实现，2026-11-19 风控接入）。
 *
 * <p>为什么不用 {@code String.contains()}：词库上百条后，每句话都要做 N 次子串扫描，
 * 单条消息的复杂度是 O(词数 × 文本长度)；聊天是高频链路，这样会把 CPU 打满。
 * 前缀树把「同一前缀的词」合并成一条路径，匹配复杂度降到 O(文本长度 × 最长词长)，
 * 且与词库规模无关。</p>
 *
 * <p>词库粒度约定：只收完整词组，不放单字（如「操」）。否则「操作」「操场」会被误杀，
 * 这类误杀比漏拦更伤业务——客户正常咨询被弹「包含违禁词」直接就走了。</p>
 *
 * <p>匹配口径：大小写不敏感（ASCII 字母统一转小写比较），不做空格/标点的「跳字符」归一，
 * 避免把「加 V 领券」这类正常话术误判成广告导流。</p>
 *
 * <p>线程安全：词树本身不可变，改词库走 {@link #reload(Collection)} —— 新建一棵树后整体替换
 * {@code volatile} 引用，读线程要么看到旧树、要么看到新树，不会读到「删了一半」的中间态。
 * 因此本类可以作为单例 Bean 注入，并在管理端改词后立刻生效（无需重启）。</p>
 */
public final class SensitiveWordFilter {

    /** 当前生效的词树快照：整体替换，不做增量修改。 */
    private volatile Core core;

    public SensitiveWordFilter(Collection<String> words) {
        this.core = Core.build(words);
    }

    /**
     * 从 classpath 读取词库（一行一个词，{@code #} 开头为注释，空行忽略）。
     *
     * @throws IllegalStateException 资源不存在或读取失败——敏感词库缺失属于启动期配置错误，
     *                               必须让应用起不来，而不是静默降级成「全放行」
     */
    public static SensitiveWordFilter fromClasspath(String resource) {
        ClassLoader loader = SensitiveWordFilter.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("敏感词库资源不存在: " + resource);
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return new SensitiveWordFilter(lines);
        } catch (IOException e) {
            throw new IllegalStateException("敏感词库读取失败: " + resource, e);
        }
    }

    /**
     * 运行期换词库（管理端增删改 / 启停后调用）。
     * <p>整棵树重建再替换引用：旧引用上的读操作不受影响，不会出现半成品词库。
     * 词条数量通常在几百级别，重建成本可忽略。</p>
     *
     * @param words 新的完整词表（{@code null}/空集合表示「一条都不拦」）
     */
    public void reload(Collection<String> words) {
        this.core = Core.build(words);
    }

    /**
     * 文本是否命中任一敏感词。
     *
     * @param text 待检文本，{@code null}/空串一律视为不含敏感词
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Core snapshot = core;
        if (snapshot.root.children.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (matchEnd(snapshot.root, text, i) > i) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把命中的敏感词按字符逐个替换成 {@code replaceChar}（长度不变的打码）。
     * <p>连续多个命中、相邻命中都会全部打码；未命中的字符原样保留。</p>
     *
     * @param text        待处理文本，{@code null}/空串原样返回
     * @param replaceChar 打码字符，通常传 {@code '*'}
     */
    public String replace(String text, char replaceChar) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Core snapshot = core;
        if (snapshot.root.children.isEmpty()) {
            return text;
        }
        boolean[] masked = new boolean[text.length()];
        boolean anyHit = false;
        for (int i = 0; i < text.length(); i++) {
            int end = matchEnd(snapshot.root, text, i);
            if (end > i) {
                Arrays.fill(masked, i, end, true);
                anyHit = true;
                i = end - 1;
            }
        }
        if (!anyHit) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (masked[i]) {
                chars[i] = replaceChar;
            }
        }
        return new String(chars);
    }

    /** 生效词条数（含重复词，重复词对匹配无影响，仅用于观测）。 */
    public int size() {
        return core.size;
    }

    /** 词库是否为空：为空时不拦截任何内容，便于测试与灰度。 */
    public boolean isEmpty() {
        return core.root.children.isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /* 前缀树                                                              */
    /* ------------------------------------------------------------------ */

    /** 返回从 {@code start} 起最长命中的结束下标（左闭右开）；未命中返回 -1。 */
    private static int matchEnd(Node root, String text, int start) {
        Node node = root;
        int end = -1;
        for (int i = start; i < text.length(); i++) {
            node = node.children.get(lower(text.charAt(i)));
            if (node == null) {
                return end;
            }
            if (node.terminal) {
                end = i + 1;
            }
        }
        return end;
    }

    /** 词条归一：去首尾空白，跳过注释行与空行。 */
    private static String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        String trimmed = word.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return "";
        }
        return trimmed;
    }

    /** 大小写不敏感：只折叠 ASCII 字母，中文与全角字符保持原样。 */
    private static char lower(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + ('a' - 'A')) : c;
    }

    /** 不可变词树快照：整体构建、整体替换。 */
    private static final class Core {
        private final Node root;
        private final int size;

        private Core(Node root, int size) {
            this.root = root;
            this.size = size;
        }

        static Core build(Collection<String> words) {
            Node root = new Node();
            int count = 0;
            if (words != null) {
                for (String word : words) {
                    String normalized = normalizeWord(word);
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    Node node = root;
                    for (int i = 0; i < normalized.length(); i++) {
                        char c = lower(normalized.charAt(i));
                        node = node.children.computeIfAbsent(c, k -> new Node());
                    }
                    node.terminal = true;
                    count++;
                }
            }
            return new Core(root, count);
        }
    }

    /** 前缀树节点：子节点表 + 是否为词尾。 */
    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>(4);
        private boolean terminal;
    }
}
