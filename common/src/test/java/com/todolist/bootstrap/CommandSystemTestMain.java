package com.todolist.bootstrap;

/**
 * 命令系统离线测试套件入口，串行执行纯逻辑自检与真实命令集成自检。
 */
public final class CommandSystemTestMain {

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandSystemTestMain() {
    }

    /**
     * 程序入口，依次执行命令系统相关的离线测试。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一测试失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        CommandInputNormalizerTestMain.main(args);
        CommandBootstrapIntegrationTestMain.main(args);
    }
}
