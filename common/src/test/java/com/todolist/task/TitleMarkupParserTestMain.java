package com.todolist.task;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;

/**
 * TitleMarkupParserTestMain 覆盖标题内联标记解析的合法、非法与降级场景。
 */
public final class TitleMarkupParserTestMain {
    /**
     * 工具类不需要实例化。
     */
    private TitleMarkupParserTestMain() {
    }

    /**
     * 执行标题标记解析测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldParsePlainTitleAsSingleText", TitleMarkupParserTestMain::shouldParsePlainTitleAsSingleText);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldParseItemMarkupIntoSegments", TitleMarkupParserTestMain::shouldParseItemMarkupIntoSegments);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldParseMultipleItemsWithTextBetween", TitleMarkupParserTestMain::shouldParseMultipleItemsWithTextBetween);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldNormalizeNamespaceAndCase", TitleMarkupParserTestMain::shouldNormalizeNamespaceAndCase);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldKeepUnclosedMarkupAsText", TitleMarkupParserTestMain::shouldKeepUnclosedMarkupAsText);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldKeepInvalidItemIdAsText", TitleMarkupParserTestMain::shouldKeepInvalidItemIdAsText);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldKeepEmptyMarkupAsText", TitleMarkupParserTestMain::shouldKeepEmptyMarkupAsText);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldKeepNestedBracketAsText", TitleMarkupParserTestMain::shouldKeepNestedBracketAsText);
        GuiTestSupport.runTestCase("TitleMarkupParserTestMain.shouldBuildConsoleTextAndVisibleLength", TitleMarkupParserTestMain::shouldBuildConsoleTextAndVisibleLength);
    }

    /**
     * 验证普通标题解析为单一文本段。
     */
    private static void shouldParsePlainTitleAsSingleText() {
        List<TitleSegment> segments = TitleMarkupParser.parse("收集铁锭 ×64");
        GuiTestSupport.assertEquals(
                List.of(TitleSegment.text("收集铁锭 ×64")),
                segments,
                "普通标题应解析为单一文本段"
        );
    }

    /**
     * 验证标准物品标记解析为文本 + 物品 + 文本三段。
     */
    private static void shouldParseItemMarkupIntoSegments() {
        List<TitleSegment> segments = TitleMarkupParser.parse("收集 [item:minecraft:iron_ingot] ×64");
        GuiTestSupport.assertEquals(
                List.of(
                        TitleSegment.text("收集 "),
                        TitleSegment.item("minecraft:iron_ingot"),
                        TitleSegment.text(" ×64")
                ),
                segments,
                "标准标记应解析为三段"
        );
    }

    /**
     * 验证多个物品标记连续出现时的分段。
     */
    private static void shouldParseMultipleItemsWithTextBetween() {
        List<TitleSegment> segments = TitleMarkupParser.parse("[item:minecraft:stone]和[item:minecraft:dirt]");
        GuiTestSupport.assertEquals(
                List.of(
                        TitleSegment.item("minecraft:stone"),
                        TitleSegment.text("和"),
                        TitleSegment.item("minecraft:dirt")
                ),
                segments,
                "连续物品标记应正确分段"
        );
    }

    /**
     * 验证省略命名空间补默认命名空间、大写输入归一为小写。
     */
    private static void shouldNormalizeNamespaceAndCase() {
        List<TitleSegment> segments = TitleMarkupParser.parse("[item:IRON_INGOT]");
        GuiTestSupport.assertEquals(
                List.of(TitleSegment.item("minecraft:iron_ingot")),
                segments,
                "无命名空间应补 minecraft 且归一为小写"
        );
    }

    /**
     * 验证未闭合标记按普通文本保留。
     */
    private static void shouldKeepUnclosedMarkupAsText() {
        List<TitleSegment> segments = TitleMarkupParser.parse("收集 [item:minecraft:iron_ingot ×64");
        GuiTestSupport.assertEquals(
                List.of(TitleSegment.text("收集 [item:minecraft:iron_ingot ×64")),
                segments,
                "未闭合标记应按普通文本保留"
        );
    }

    /**
     * 验证资源 ID 格式非法的标记按普通文本保留。
     */
    private static void shouldKeepInvalidItemIdAsText() {
        List<TitleSegment> segments = TitleMarkupParser.parse("看这个 [item:iron:ingot:extra] 标记");
        GuiTestSupport.assertEquals(
                List.of(TitleSegment.text("看这个 [item:iron:ingot:extra] 标记")),
                segments,
                "多段冒号的非法资源 ID 应按普通文本保留"
        );
    }

    /**
     * 验证空标记按普通文本保留。
     */
    private static void shouldKeepEmptyMarkupAsText() {
        List<TitleSegment> segments = TitleMarkupParser.parse("空标记 [item:] 测试");
        GuiTestSupport.assertEquals(
                List.of(TitleSegment.text("空标记 [item:] 测试")),
                segments,
                "空标记应按普通文本保留"
        );
    }

    /**
     * 验证标记内含嵌套方括号时整段按普通文本保留。
     */
    private static void shouldKeepNestedBracketAsText() {
        List<TitleSegment> segments = TitleMarkupParser.parse("嵌套 [item:minecraft:[bad]] 标记");
        GuiTestSupport.assertEquals(
                List.of(TitleSegment.text("嵌套 [item:minecraft:[bad]] 标记")),
                segments,
                "嵌套方括号的非法标记应整段按普通文本保留"
        );
    }

    /**
     * 验证控制台降级文本与可见长度计算。
     */
    private static void shouldBuildConsoleTextAndVisibleLength() {
        String title = "收集 [item:minecraft:iron_ingot] ×64";
        GuiTestSupport.assertEquals(
                "收集 minecraft:iron_ingot ×64",
                TitleMarkupParser.toConsoleText(title),
                "控制台降级应保留资源 ID"
        );
        GuiTestSupport.assertEquals(
                "收集  ×64".length(),
                TitleMarkupParser.visibleTextLength(title),
                "可见长度不应计入标记本身"
        );
        GuiTestSupport.assertEquals(
                0,
                TitleMarkupParser.visibleTextLength(null),
                "空标题可见长度应为 0"
        );
        GuiTestSupport.assertEquals(
                0,
                TitleMarkupParser.parse(null).size(),
                "空标题应解析为空分段"
        );
    }
}
