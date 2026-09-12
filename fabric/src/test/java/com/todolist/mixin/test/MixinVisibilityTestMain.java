package com.todolist.mixin.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Mixin 可见性离线自测入口。
 * SpongePowered Mixin 要求被注入类中所有非编译器生成的成员必须为 private：
 * 非 private 静态方法会在应用阶段抛 {@code InvalidMixinException} 并导致目标类加载失败，
 * 曾引发「进档提示无效的玩家数据 / 存档列表异常」（见 docs/feat-trigger-completion.md 难点 11）。
 * 该错误编译期与常规单元测试均无法发现，只有真正进档才暴露，
 * 这里用反射把可见性约束固化为构建期检查，防止回归。
 */
public final class MixinVisibilityTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private MixinVisibilityTestMain() {
    }

    /**
     * 程序入口，串行执行全部 Mixin 可见性自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        runCase("ResultSlotMixin", () -> assertAllMembersPrivate(com.todolist.mixin.ResultSlotMixin.class));
        runCase("PlayerAdvancementsMixin", () -> assertAllMembersPrivate(com.todolist.mixin.PlayerAdvancementsMixin.class));
    }

    /**
     * 执行单个用例并输出结果，失败时以非零退出码终止构建。
     *
     * @param name 用例名
     * @param caseBody 用例体
     */
    private static void runCase(String name, Runnable caseBody) {
        System.out.println("[MIXIN][CASE ][RUN ] " + name);
        long start = System.currentTimeMillis();
        try {
            caseBody.run();
        } catch (Throwable failure) {
            System.out.println("[MIXIN][CASE ][FAIL] " + name + " : " + failure.getMessage());
            System.exit(1);
        }
        System.out.println("[MIXIN][CASE ][PASS] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
    }

    /**
     * 断言 Mixin 类的全部声明成员（跳过编译器生成的 synthetic 成员）均为 private。
     *
     * @param mixinClass 待检查的 Mixin 类
     */
    private static void assertAllMembersPrivate(Class<?> mixinClass) {
        for (Method method : mixinClass.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (!Modifier.isPrivate(method.getModifiers())) {
                throw new IllegalStateException("Mixin " + mixinClass.getSimpleName()
                        + " 存在非 private 方法 " + method.getName()
                        + "，运行期会抛 InvalidMixinException 导致目标类加载失败");
            }
        }
        for (Field field : mixinClass.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            if (!Modifier.isPrivate(field.getModifiers())) {
                throw new IllegalStateException("Mixin " + mixinClass.getSimpleName()
                        + " 存在非 private 字段 " + field.getName());
            }
        }
    }
}
