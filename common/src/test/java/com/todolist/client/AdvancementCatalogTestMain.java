package com.todolist.client;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/**
 * 客户端服务端权威进度目录的离线自测入口。
 * 覆盖三件事：目录缓存读写、切换存储域后失效、以及网络包的编解码往返。
 * 该目录用于修复「新存档下选择进度列表恒为空」：客户端自带的进度列表只含
 * 已解锁 / 可见进度，必须改用服务端全量目录。
 */
public final class AdvancementCatalogTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private AdvancementCatalogTestMain() {
    }

    /**
     * 程序入口，串行执行进度目录相关自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.bootstrapEnvironment();
        GuiTestSupport.runTestCase("AdvancementCatalogTestMain.shouldKeepEntriesWithinSameNamespace",
                AdvancementCatalogTestMain::shouldKeepEntriesWithinSameNamespace);
        GuiTestSupport.runTestCase("AdvancementCatalogTestMain.shouldDropEntriesWhenNamespaceChanges",
                AdvancementCatalogTestMain::shouldDropEntriesWhenNamespaceChanges);
        GuiTestSupport.runTestCase("AdvancementCatalogTestMain.shouldRoundTripEntriesOverBuffer",
                AdvancementCatalogTestMain::shouldRoundTripEntriesOverBuffer);
    }

    /**
     * 校验同一存储域下目录可正常读出。
     */
    private static void shouldKeepEntriesWithinSameNamespace() {
        DataPathProvider.setStorageNamespace("catalog-same-world");
        AdvancementCatalog.apply("catalog-same-world", List.of(
                new AdvancementCatalog.Entry("minecraft:story/mine_stone", "石器时代"),
                new AdvancementCatalog.Entry("minecraft:story/root", "Minecraft")));

        List<AdvancementCatalog.Entry> entries = AdvancementCatalog.getEntries();
        GuiTestSupport.assertEquals(2, entries.size(), "同一存储域下应保留全部目录条目");
        GuiTestSupport.assertEquals("minecraft:story/mine_stone", entries.get(0).id(), "目录条目应保持服务端下发顺序");
        GuiTestSupport.assertEquals("石器时代", entries.get(0).title(), "目录条目应保留展示名");
        AdvancementCatalog.clear();
    }

    /**
     * 校验切换存储域后旧目录立即失效，不把上一个存档的进度带入新存档。
     */
    private static void shouldDropEntriesWhenNamespaceChanges() {
        DataPathProvider.setStorageNamespace("catalog-world-a");
        AdvancementCatalog.apply("catalog-world-a", List.of(
                new AdvancementCatalog.Entry("minecraft:story/root", "Minecraft")));
        GuiTestSupport.assertEquals(1, AdvancementCatalog.getEntries().size(), "同域读取应命中缓存");

        DataPathProvider.setStorageNamespace("catalog-world-b");
        GuiTestSupport.assertEquals(0, AdvancementCatalog.getEntries().size(), "切换存储域后目录应失效");
        GuiTestSupport.assertEquals(0, AdvancementCatalog.getEntries().size(), "失效后应保持为空直到再次下发");

        AdvancementCatalog.clear();
        DataPathProvider.resetStorageNamespace();
    }

    /**
     * 校验目录网络包编解码往返一致（id 与展示名都不丢失）。
     */
    private static void shouldRoundTripEntriesOverBuffer() {
        List<AdvancementCatalog.Entry> source = List.of(
                new AdvancementCatalog.Entry("minecraft:story/mine_stone", "石器时代"),
                new AdvancementCatalog.Entry("minecraft:nether/root", "下界"),
                new AdvancementCatalog.Entry("minecraft:end/kill_dragon", "解放末地"));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeAdvancementCatalog(buf, source);
        List<AdvancementCatalog.Entry> restored = TaskPackets.readAdvancementCatalog(buf);

        GuiTestSupport.assertEquals(source.size(), restored.size(), "编解码后条目数量应一致");
        for (int i = 0; i < source.size(); i++) {
            GuiTestSupport.assertEquals(source.get(i).id(), restored.get(i).id(), "编解码后进度 ID 应一致");
            GuiTestSupport.assertEquals(source.get(i).title(), restored.get(i).title(), "编解码后展示名应一致");
        }
    }
}
