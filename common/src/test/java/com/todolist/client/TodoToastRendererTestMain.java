package com.todolist.client;

import com.todolist.gui.testsupport.GuiTestSupport;

/**
 * TodoToastRenderer 离线自测入口，覆盖提示入队、容量上限与空标题过滤。
 */
public final class TodoToastRendererTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TodoToastRendererTestMain() {
    }

    /**
     * 程序入口，串行执行浮动提示渲染器的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TodoToastRendererTestMain.shouldCapActiveToastEntries", TodoToastRendererTestMain::shouldCapActiveToastEntries);
        GuiTestSupport.runTestCase("TodoToastRendererTestMain.shouldIgnoreBlankTitle", TodoToastRendererTestMain::shouldIgnoreBlankTitle);
    }

    /**
     * 校验同时展示的提示条数量被限制在上限内。
     */
    private static void shouldCapActiveToastEntries() {
        GuiTestSupport.resetState();
        TodoToastRenderer.clearForTest();
        for (int index = 0; index < 5; index++) {
            TodoToastRenderer.show("Task " + index);
        }

        GuiTestSupport.assertEquals(3, TodoToastRenderer.activeCountForTest(), "同时展示的提示条应限制为 3 条");
        TodoToastRenderer.clearForTest();
    }

    /**
     * 校验空标题不会进入提示队列。
     */
    private static void shouldIgnoreBlankTitle() {
        GuiTestSupport.resetState();
        TodoToastRenderer.clearForTest();
        TodoToastRenderer.show(null);
        TodoToastRenderer.show("");

        GuiTestSupport.assertEquals(0, TodoToastRenderer.activeCountForTest(), "空标题不应加入提示队列");
        TodoToastRenderer.clearForTest();
    }
}
