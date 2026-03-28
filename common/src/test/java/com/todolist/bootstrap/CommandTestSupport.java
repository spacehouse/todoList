package com.todolist.bootstrap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 命令系统离线自测支撑工具：负责输出具名测试日志，并按约定发现和执行 `should*` 测试用例。
 */
public final class CommandTestSupport {

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandTestSupport() {
    }

    /**
     * 运行具名命令测试组，并输出开始、通过和失败日志。
     *
     * @param groupName 测试组名称
     * @param action 测试组执行逻辑
     * @throws Exception 测试组执行失败时向上抛出异常
     */
    public static void runTestGroup(String groupName, ThrowingRunnable action) throws Exception {
        long startNs = System.nanoTime();
        System.out.println("[CMD][GROUP][RUN ] " + groupName);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[CMD][GROUP][PASS] " + groupName + " (" + durationMs + " ms)");
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[CMD][GROUP][FAIL] " + groupName + " (" + durationMs + " ms)");
            throw new IllegalStateException("命令测试组失败: " + groupName, exception);
        } catch (AssertionError assertionError) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[CMD][GROUP][FAIL] " + groupName + " (" + durationMs + " ms)");
            throw assertionError;
        }
    }

    /**
     * 运行具名命令测试用例，并在失败时补充更明确的用例名称。
     *
     * @param caseName 测试用例名称
     * @param action 测试用例执行逻辑
     * @throws Exception 测试用例执行失败时向上抛出异常
     */
    public static void runTestCase(String caseName, ThrowingRunnable action) throws Exception {
        long startNs = System.nanoTime();
        System.out.println("[CMD][CASE ][RUN ] " + caseName);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[CMD][CASE ][PASS] " + caseName + " (" + durationMs + " ms)");
        } catch (AssertionError assertionError) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[CMD][CASE ][FAIL] " + caseName + " (" + durationMs + " ms)");
            throw new AssertionError("命令测试用例失败: " + caseName + " -> " + assertionError.getMessage(), assertionError);
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[CMD][CASE ][FAIL] " + caseName + " (" + durationMs + " ms)");
            throw new IllegalStateException("命令测试用例异常: " + caseName, exception);
        }
    }

    /**
     * 自动发现并运行目标类中所有满足约定的 `should*` 静态无参测试方法。
     *
     * @param owner 声明测试用例的类
     * @throws Exception 任一测试用例失败时向上抛出异常
     */
    public static void runAllShouldCases(Class<?> owner) throws Exception {
        List<Method> cases = Arrays.stream(owner.getDeclaredMethods())
                .filter(CommandTestSupport::isShouldCase)
                .sorted(Comparator.comparing(Method::getName))
                .toList();
        for (Method method : cases) {
            runTestCase(owner.getSimpleName() + "." + method.getName(), () -> invokeCase(method));
        }
    }

    /**
     * 判断方法是否满足命令离线自测的约定格式。
     *
     * @param method 待判断的方法
     * @return true 表示该方法是一个可执行的 `should*` 测试用例
     */
    private static boolean isShouldCase(Method method) {
        return method.getName().startsWith("should")
                && method.getParameterCount() == 0
                && method.getReturnType() == Void.TYPE
                && Modifier.isStatic(method.getModifiers());
    }

    /**
     * 通过反射执行单个静态测试用例方法，并解包真实异常。
     *
     * @param method 待执行的测试用例方法
     * @throws Exception 执行失败时抛出原始异常
     */
    private static void invokeCase(Method method) throws Exception {
        try {
            method.setAccessible(true);
            method.invoke(null);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof AssertionError assertionError) {
                throw assertionError;
            }
            throw new IllegalStateException("执行命令测试用例失败: " + method.getName(), cause);
        } catch (IllegalAccessException illegalAccessException) {
            throw new IllegalStateException("无法访问命令测试用例: " + method.getName(), illegalAccessException);
        }
    }

    /**
     * 可抛出受检异常的测试执行函数式接口。
     */
    @FunctionalInterface
    public interface ThrowingRunnable {

        /**
         * 执行测试逻辑。
         *
         * @throws Exception 执行失败时抛出异常
         */
        void run() throws Exception;
    }
}
