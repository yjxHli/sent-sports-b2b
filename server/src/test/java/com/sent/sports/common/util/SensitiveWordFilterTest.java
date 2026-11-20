package com.sent.sports.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词过滤（DFA / 前缀树）单测。
 * <p>边界重点：「操」这种单字不能进词库——一旦进了，「操作」「操场」都会被误杀。
 * 词库只收完整词组（如「操你妈」），所以「操作」必须判为正常文本。</p>
 */
class SensitiveWordFilterTest {

    /** 测试用小词库：只放完整词组，不放单字，模拟真实词库的粒度要求。 */
    private static final List<String> WORDS = List.of(
            "测试违禁词", "傻X", "加V", "操你妈", "代开发票");

    private final SensitiveWordFilter filter = new SensitiveWordFilter(WORDS);

    @Test
    @DisplayName("包含敏感词：直接命中词库里的词")
    void detectsSensitiveWord() {
        assertTrue(filter.contains("你这个傻X"));
    }

    @Test
    @DisplayName("不包含敏感词：正常业务话术不误判")
    void normalTextNotFlagged() {
        assertFalse(filter.contains("请问这件球衣有 XL 码吗？"));
        assertFalse(filter.contains(""));
        assertFalse(filter.contains(null));
    }

    @Test
    @DisplayName("多处敏感词：同一句话命中多个词仍然只要一个布尔结果")
    void detectsMultipleSensitiveWords() {
        assertTrue(filter.contains("加V 详聊，还能代开发票"));
    }

    @Test
    @DisplayName("类似词汇但正常：「操作」不能被「操」误伤（词库不收单字）")
    void similarButNormalTextNotFlagged() {
        assertFalse(filter.contains("操作很简单，按说明书来就行"));
        assertFalse(filter.contains("请到操场东门取货"));
        // 但完整词组必须命中
        assertTrue(filter.contains("操你妈"));
    }

    @Test
    @DisplayName("大小写不敏感：傻x / 傻X 都要拦")
    void matchingIsCaseInsensitive() {
        assertTrue(filter.contains("傻x"));
        assertTrue(filter.contains("加v"));
    }

    @Test
    @DisplayName("替换：命中部分按字符数打码，正常字符保持原样")
    void replaceMasksOnlyHitRanges() {
        assertEquals("你这个**", filter.replace("你这个傻X", '*'));
        assertEquals("**详聊", filter.replace("加V详聊", '*'));
        assertEquals("操作很简单", filter.replace("操作很简单", '*'));
    }

    @Test
    @DisplayName("替换：多词命中、相邻命中都要全部打码，且长度不变")
    void replaceHandlesMultipleAndOverlappingHits() {
        assertEquals("*******，还能****", filter.replace("测试违禁词傻X，还能代开发票", '*'));
    }

    @Test
    @DisplayName("替换：空文本/无命中文本原样返回")
    void replaceKeepsTextWhenNothingMatched() {
        assertEquals("", filter.replace("", '*'));
        assertEquals("森特体育", filter.replace("森特体育", '*'));
    }

    @Test
    @DisplayName("词库为空时不应拦截任何文本")
    void emptyDictionaryNeverBlocks() {
        SensitiveWordFilter empty = new SensitiveWordFilter(List.of());
        assertFalse(empty.contains("测试违禁词"));
        assertEquals("测试违禁词", empty.replace("测试违禁词", '*'));
    }

    @Test
    @DisplayName("词库加载：默认资源文件必须含演示词（测试违禁词 / 傻X / 加V）")
    void loadsDefaultDictionaryFromClasspath() {
        SensitiveWordFilter loaded = SensitiveWordFilter.fromClasspath("sensitive_words.txt");
        assertTrue(loaded.contains("测试违禁词"), "E2E 断言依赖这个词必须存在");
        assertTrue(loaded.contains("傻X"));
        assertTrue(loaded.contains("加V"));
        assertTrue(loaded.size() >= 20, "演示词库至少要有 20 个词，避免接入后形同虚设");
    }

    @Test
    @DisplayName("词库加载：资源不存在时必须显式失败，而不是静默变成空词库")
    void missingDictionaryFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> SensitiveWordFilter.fromClasspath("no_such_words.txt"));
    }

    @Test
    @DisplayName("词库加载：空白行与 # 注释不算词")
    void skipsBlankLinesAndComments() {
        SensitiveWordFilter loaded = SensitiveWordFilter.fromClasspath("sensitive_words.txt");
        assertFalse(loaded.contains("#"));
        assertFalse(loaded.contains(" "));
    }

    @Test
    @DisplayName("运行期重载：管理端改词库后立即生效，不需要重启")
    void reloadSwapsDictionaryAtRuntime() {
        SensitiveWordFilter runtime = new SensitiveWordFilter(List.of("加V"));
        assertTrue(runtime.contains("加V"));

        runtime.reload(List.of("测试违禁词"));

        assertFalse(runtime.contains("加V"), "旧词必须立刻失效");
        assertTrue(runtime.contains("测试违禁词"), "新词必须立刻生效");
        assertEquals(1, runtime.size());
    }

    @Test
    @DisplayName("运行期重载：管理员清空词库后应放行一切（而不是继续用旧的）")
    void reloadWithEmptyDictionaryStopsBlocking() {
        SensitiveWordFilter runtime = new SensitiveWordFilter(List.of("加V"));

        runtime.reload(List.of());

        assertTrue(runtime.isEmpty());
        assertFalse(runtime.contains("加V"));
        assertEquals(0, runtime.replace("加V", '*').chars().filter(c -> c == '*').count());
    }
}
