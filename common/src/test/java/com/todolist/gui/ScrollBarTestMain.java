package com.todolist.gui;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * 滚动条组件离线自测入口：覆盖值裁剪、悬停渲染与拖拽滚动等核心行为。
 */
public final class ScrollBarTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ScrollBarTestMain() {
    }

    /**
     * 程序入口，串行执行滚动条组件的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("ScrollBarTestMain.shouldClampValueAndOffsetWithinRange", ScrollBarTestMain::shouldClampValueAndOffsetWithinRange);
        GuiTestSupport.runTestCase("ScrollBarTestMain.shouldUseHoverColorAndTrackMouseOverDuringRender", ScrollBarTestMain::shouldUseHoverColorAndTrackMouseOverDuringRender);
        GuiTestSupport.runTestCase("ScrollBarTestMain.shouldUpdateValueWhenDraggingThumb", ScrollBarTestMain::shouldUpdateValueWhenDraggingThumb);
    }

    /**
     * 校验滚动值设置与偏移会被裁剪到合法范围。
     */
    private static void shouldClampValueAndOffsetWithinRange() {
        GuiTestSupport.resetState();
        ScrollBar scrollBar = new ScrollBar(4, 6, 8, 80);
        scrollBar.setMaxValue(5);

        scrollBar.setValue(99);
        GuiTestSupport.assertEquals(5, scrollBar.getValue(), "直接设置滚动值时应裁剪到最大值");

        scrollBar.offsetValue(-99);
        GuiTestSupport.assertEquals(0, scrollBar.getValue(), "偏移滚动值时应裁剪到最小值");
    }

    /**
     * 校验鼠标悬停在滑块上时会切换悬停颜色，并记录整体滚动条悬停状态。
     */
    private static void shouldUseHoverColorAndTrackMouseOverDuringRender() {
        GuiTestSupport.resetState();
        ScrollBar scrollBar = new ScrollBar(10, 20, 8, 60, 0xFF111111, 0xFF222222, 0xFF333333, 0xFF444444);
        RecordingRenderer renderer = new RecordingRenderer();

        scrollBar.render(13, 25, 120, renderer);

        GuiTestSupport.assertEquals(2, renderer.getCalls().size(), "滚动条渲染应先绘制轨道再绘制滑块");
        GuiTestSupport.assertEquals(0xFF111111, renderer.getCalls().get(0).color(), "第一笔绘制应使用轨道背景色");
        GuiTestSupport.assertEquals(0xFF333333, renderer.getCalls().get(1).color(), "鼠标悬停滑块时应使用悬停颜色");
        GuiTestSupport.assertTrue(scrollBar.wasMouseOver(), "鼠标位于滚动条区域内时应记录悬停状态");
    }

    /**
     * 校验拖拽滑块会按拖拽位移更新滚动值。
     */
    private static void shouldUpdateValueWhenDraggingThumb() {
        GuiTestSupport.resetState();
        ScrollBar scrollBar = new ScrollBar(10, 20, 8, 100);
        RecordingRenderer renderer = new RecordingRenderer();
        scrollBar.setMaxValue(100);
        scrollBar.setValue(20);

        scrollBar.render(12, 30, 300, renderer);
        scrollBar.setIsDragging(true);
        scrollBar.render(12, 95, 300, renderer);

        GuiTestSupport.assertTrue(scrollBar.isDragging(), "拖拽测试阶段应保持拖拽状态");
        GuiTestSupport.assertEquals(100, scrollBar.getValue(), "拖拽到底部后应把滚动值推进到最大值");
    }

    /**
     * 记录滚动条绘制调用，供断言渲染结果使用。
     */
    private static final class RecordingRenderer implements ScrollBar.ScrollBarRenderer {
        private final List<RectCall> calls = new ArrayList<>();

        /**
         * 记录一次矩形填充调用。
         *
         * @param x1 左上角 X
         * @param y1 左上角 Y
         * @param x2 右下角 X
         * @param y2 右下角 Y
         * @param color 颜色值
         */
        @Override
        public void fillRect(int x1, int y1, int x2, int y2, int color) {
            calls.add(new RectCall(x1, y1, x2, y2, color));
        }

        /**
         * 返回当前记录的绘制调用列表快照。
         *
         * @return 绘制调用列表快照
         */
        public List<RectCall> getCalls() {
            return List.copyOf(calls);
        }
    }

    /**
     * 矩形绘制记录：保存一次滚动条填充调用的关键参数。
     *
     * @param x1 左上角 X
     * @param y1 左上角 Y
     * @param x2 右下角 X
     * @param y2 右下角 Y
     * @param color 颜色值
     */
    private record RectCall(int x1, int y1, int x2, int y2, int color) {
    }
}
