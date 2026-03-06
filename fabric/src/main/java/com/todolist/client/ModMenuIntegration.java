package com.todolist.client;

import com.todolist.gui.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成入口。
 * 为 Mod Menu 提供配置界面工厂，以便从菜单打开本模组配置。
 */
public class ModMenuIntegration implements ModMenuApi {
    /**
     * 提供模组配置界面工厂。
     *
     * @return 配置界面工厂
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent);
    }
}
