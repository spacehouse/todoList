package com.todolist.mixin.test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mixin 注册表守护测试：fabric mixins.json 的注册项与 com.todolist.mixin 包下的
 * mixin 类一一对应，防止 1.20.1 ↔ 1.21.1 同步时漏注册/多注册/类名拼错——
 * 那类错误 mixinVisibilityTest 抓不到（被删的 mixin 不会参与可见性检查）。
 * 新增 mixin 类时必须同时更新注册表与本清单，守护测试会强制两者同步。
 */
public final class MixinConfigTestMain {
    /** 当前 mixin 包下应有的全部 mixin 类名（新增 mixin 时必须同时更新注册表与本清单）。 */
    private static final List<String> EXPECTED_MIXINS = List.of(
            "ResultSlotMixin",
            "PlayerAdvancementsMixin"
    );

    private MixinConfigTestMain() {
    }

    /**
     * 执行 mixin 注册表守护自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("src/main/resources/todolist.mixins.json"));
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        JsonArray mixins = root.getAsJsonArray("mixins");
        List<String> registered = new ArrayList<>();
        for (int i = 0; i < mixins.size(); i++) {
            registered.add(mixins.get(i).getAsString());
        }

        List<String> missing = new ArrayList<>(EXPECTED_MIXINS);
        missing.removeAll(registered);
        List<String> unexpected = new ArrayList<>(registered);
        unexpected.removeAll(EXPECTED_MIXINS);

        StringBuilder problems = new StringBuilder();
        if (!missing.isEmpty()) {
            problems.append("包内存在但未注册的 mixin: ").append(missing).append("; ");
        }
        if (!unexpected.isEmpty()) {
            problems.append("已注册但类不存在的 mixin: ").append(unexpected).append("; ");
        }
        for (String name : registered) {
            try {
                Class.forName("com.todolist.mixin." + name);
            } catch (ClassNotFoundException e) {
                problems.append("注册项类无法加载: ").append(name).append("; ");
            }
        }
        if (problems.length() > 0) {
            throw new AssertionError("mixin 注册表守护失败: " + problems);
        }
    }
}
