package com.todolist.bootstrap;

import java.util.List;

/**
 * 命令输入归一化自检入口，使用纯 Java 断言覆盖命令参数的核心归一化语义。
 */
public final class CommandInputNormalizerTestMain {

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandInputNormalizerTestMain() {
    }

    /**
     * 程序入口，执行全部归一化断言。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        try {
            CommandTestSupport.runAllShouldCases(CommandInputNormalizerTestMain.class);
        } catch (Exception exception) {
            throw new IllegalStateException("命令输入归一化自测执行失败", exception);
        }
    }

    /**
     * 校验任务列表状态参数的归一化规则。
     */
    private static void shouldNormalizeTaskListStatus() {
        assertEquals("all", CommandInputNormalizer.normalizeTaskListStatus(null), "null 状态应归一化为 all");
        assertEquals("all", CommandInputNormalizer.normalizeTaskListStatus("ALL"), "ALL 状态应归一化为 all");
        assertEquals("incomplete", CommandInputNormalizer.normalizeTaskListStatus("incomplete"), "incomplete 状态归一化失败");
        assertEquals("completed", CommandInputNormalizer.normalizeTaskListStatus("Completed"), "completed 状态归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeTaskListStatus("todo"), "非法状态应归一化为空字符串");
    }

    /**
     * 校验任务列表优先级参数的归一化规则。
     */
    private static void shouldNormalizeTaskListPriority() {
        assertEquals("all", CommandInputNormalizer.normalizeTaskListPriority(null), "null 优先级应归一化为 all");
        assertEquals("low", CommandInputNormalizer.normalizeTaskListPriority("LOW"), "low 优先级归一化失败");
        assertEquals("medium", CommandInputNormalizer.normalizeTaskListPriority("Medium"), "medium 优先级归一化失败");
        assertEquals("high", CommandInputNormalizer.normalizeTaskListPriority("high"), "high 优先级归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeTaskListPriority("urgent"), "非法优先级应归一化为空字符串");
    }

    /**
     * 校验任务清理相关参数的归一化规则。
     */
    private static void shouldNormalizeTaskCleanInputs() {
        assertEquals("personal", CommandInputNormalizer.normalizeTaskCleanScope("personal"), "clean scope personal 归一化失败");
        assertEquals("team", CommandInputNormalizer.normalizeTaskCleanScope("TEAM"), "clean scope team 归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeTaskCleanScope("guild"), "非法 clean scope 应归一化为空字符串");

        assertEquals("current", CommandInputNormalizer.normalizeTaskCleanProjectSelector("CURRENT"), "clean project current 归一化失败");
        assertEquals("star", CommandInputNormalizer.normalizeTaskCleanProjectSelector("star"), "clean project star 归一化失败");
        assertEquals("all", CommandInputNormalizer.normalizeTaskCleanProjectSelector("all"), "clean project all 归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeTaskCleanProjectSelector("favorite"), "非法 clean project 应归一化为空字符串");

        assertEquals("incomplete", CommandInputNormalizer.normalizeTaskCleanStatus("INCOMPLETE"), "clean status incomplete 归一化失败");
        assertEquals("completed", CommandInputNormalizer.normalizeTaskCleanStatus("completed"), "clean status completed 归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeTaskCleanStatus("pending"), "非法 clean status 应归一化为空字符串");
    }

    /**
     * 校验项目相关参数的归一化规则。
     */
    private static void shouldNormalizeProjectInputs() {
        assertEquals("personal", CommandInputNormalizer.normalizeProjectScope("personal"), "project scope personal 归一化失败");
        assertEquals("team", CommandInputNormalizer.normalizeProjectScope("TEAM"), "project scope team 归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeProjectScope("coop"), "非法 project scope 应归一化为空字符串");

        assertEquals("all", CommandInputNormalizer.normalizeProjectListMode("all"), "project list all 归一化失败");
        assertEquals("current", CommandInputNormalizer.normalizeProjectListMode("CURRENT"), "project list current 归一化失败");
        assertEquals("star", CommandInputNormalizer.normalizeProjectListMode("star"), "project list star 归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeProjectListMode("mine"), "非法 project list mode 应归一化为空字符串");
    }

    /**
     * 校验布尔开关参数的归一化规则。
     */
    private static void shouldNormalizeToggleState() {
        assertEquals(Boolean.TRUE, CommandInputNormalizer.normalizeToggleState("on"), "on 应归一化为 true");
        assertEquals(Boolean.TRUE, CommandInputNormalizer.normalizeToggleState("ENABLE"), "enable 应归一化为 true");
        assertEquals(Boolean.TRUE, CommandInputNormalizer.normalizeToggleState("1"), "1 应归一化为 true");
        assertEquals(Boolean.FALSE, CommandInputNormalizer.normalizeToggleState("off"), "off 应归一化为 false");
        assertEquals(Boolean.FALSE, CommandInputNormalizer.normalizeToggleState("disabled"), "disabled 应归一化为 false");
        assertEquals(Boolean.FALSE, CommandInputNormalizer.normalizeToggleState("0"), "0 应归一化为 false");
        assertEquals(null, CommandInputNormalizer.normalizeToggleState("maybe"), "非法 toggle 输入应归一化为 null");
        assertEquals(null, CommandInputNormalizer.normalizeToggleState(null), "null toggle 输入应归一化为 null");
    }

    /**
     * 校验项目成员角色参数的归一化规则。
     */
    private static void shouldNormalizeProjectMemberRole() {
        assertEquals("lead", CommandInputNormalizer.normalizeProjectMemberRole("LEAD"), "lead 角色归一化失败");
        assertEquals("member", CommandInputNormalizer.normalizeProjectMemberRole("member"), "member 角色归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeProjectMemberRole("manager"), "非法项目成员角色应归一化为空字符串");
        assertEquals("", CommandInputNormalizer.normalizeProjectMemberRole(null), "null 项目成员角色应归一化为空字符串");
    }

    /**
     * 校验命令权限模式参数的归一化规则。
     */
    private static void shouldNormalizeCommandAccessMode() {
        assertEquals("op_only", CommandInputNormalizer.normalizeCommandAccessMode("OP_ONLY"), "op_only 权限模式归一化失败");
        assertEquals("view_only", CommandInputNormalizer.normalizeCommandAccessMode("view-only"), "view_only 权限模式归一化失败");
        assertEquals("full", CommandInputNormalizer.normalizeCommandAccessMode("full"), "full 权限模式归一化失败");
        assertEquals("", CommandInputNormalizer.normalizeCommandAccessMode("guest"), "非法权限模式应归一化为空字符串");
        assertEquals("", CommandInputNormalizer.normalizeCommandAccessMode(null), "null 权限模式应归一化为空字符串");
    }

    /**
     * 校验 HUD 星标项目列表的增删与去重逻辑。
     */
    private static void shouldApplyHudStarredProjectState() {
        assertListEquals(
                List.of("project-a", "project-b"),
                CommandInputNormalizer.applyHudStarredProjectState(List.of("project-a", "project-b"), "project-b", true),
                "重复添加星标项目时不应产生重复项"
        );
        assertListEquals(
                List.of("project-a", "project-b", "project-c"),
                CommandInputNormalizer.applyHudStarredProjectState(List.of("project-a", "project-b"), " project-c ", true),
                "新增星标项目时应追加归一化后的项目 ID"
        );
        assertListEquals(
                List.of("project-b"),
                CommandInputNormalizer.applyHudStarredProjectState(List.of("project-a", "project-b"), "project-a", false),
                "取消星标时应移除目标项目 ID"
        );
        assertListEquals(
                List.of("project-a"),
                CommandInputNormalizer.applyHudStarredProjectState(List.of("project-a", "", "project-a"), " ", true),
                "空项目 ID 不应污染星标项目列表"
        );
    }

    /**
     * 断言两个对象相等。
     *
     * @param expected 预期值
     * @param actual 实际值
     * @param message 失败说明
     */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 断言两个列表内容完全一致。
     *
     * @param expected 预期列表
     * @param actual 实际列表
     * @param message 失败说明
     */
    private static void assertListEquals(List<String> expected, List<String> actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
