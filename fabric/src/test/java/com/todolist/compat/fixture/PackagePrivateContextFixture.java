package com.todolist.compat.fixture;

/**
 * 包私有上下文夹具类。
 *
 * <p>用于冒烟测试复现 Fabric 的包私有内部类场景：
 * {@code ServerPlayNetworkAddon$ContextImpl} 本身是包私有类，
 * 其公开的 {@code player()} 方法跨包反射调用会被 Java 访问控制拒绝。
 * 本类刻意声明为包私有且位于不同包，配合反射调用即可验证
 * 兼容层是否正确放开了可访问性。</p>
 */
class PackagePrivateContextFixture {

    /**
     * 包私有类禁止外部直接实例化，测试侧通过反射构造。
     */
    PackagePrivateContextFixture() {
    }

    /**
     * 公开访问器方法，模拟 ContextImpl#player 形状。
     *
     * @return 固定标记值
     */
    public String player() {
        return "fixture-player";
    }
}
