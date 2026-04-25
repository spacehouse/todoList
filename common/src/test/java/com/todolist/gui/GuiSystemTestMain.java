package com.todolist.gui;

import com.todolist.client.ClientTaskStorageHelperTestMain;
import com.todolist.client.TodoHudRendererTestMain;
import com.todolist.gui.testsupport.GuiTestSupport;

/**
 * GUI 自测总入口：串行执行当前已经落地的离线 GUI 自动化测试。
 */
public final class GuiSystemTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private GuiSystemTestMain() {
    }

    /**
     * 程序入口，按顺序执行 GUI 相关的离线自测。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一测试失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        GuiTestSupport.bootstrapEnvironment();
        GuiTestSupport.runTestGroup("ClientTaskStorageHelperTestMain", () -> ClientTaskStorageHelperTestMain.main(args));
        GuiTestSupport.runTestGroup("PersistenceSafetyTestMain", () -> PersistenceSafetyTestMain.main(args));
        GuiTestSupport.runTestGroup("ProjectListWidgetTestMain", () -> ProjectListWidgetTestMain.main(args));
        GuiTestSupport.runTestGroup("TaskListWidgetTestMain", () -> TaskListWidgetTestMain.main(args));
        GuiTestSupport.runTestGroup("ScrollBarTestMain", () -> ScrollBarTestMain.main(args));
        GuiTestSupport.runTestGroup("AddProjectScreenTestMain", () -> AddProjectScreenTestMain.main(args));
        GuiTestSupport.runTestGroup("ConfigScreenTestMain", () -> ConfigScreenTestMain.main(args));
        GuiTestSupport.runTestGroup("ConfirmDeleteProjectScreenTestMain", () -> ConfirmDeleteProjectScreenTestMain.main(args));
        GuiTestSupport.runTestGroup("AddMemberScreenTestMain", () -> AddMemberScreenTestMain.main(args));
        GuiTestSupport.runTestGroup("ProjectSettingsScreenTestMain", () -> ProjectSettingsScreenTestMain.main(args));
        GuiTestSupport.runTestGroup("TodoScreenTestMain", () -> TodoScreenTestMain.main(args));
        GuiTestSupport.runTestGroup("TodoHudRendererTestMain", () -> TodoHudRendererTestMain.main(args));
    }
}
