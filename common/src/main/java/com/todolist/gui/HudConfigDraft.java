package com.todolist.gui;

import com.todolist.config.ModConfig;

/**
 * HUD 配置页草稿状态。
 * 负责承载配置页编辑期间的 HUD 字段与预览位置，只有保存时才统一持久化到配置对象。
 */
final class HudConfigDraft {
    int hudWidth;
    int hudMaxHeight;
    int hudTodoLimit;
    int hudDoneLimit;
    double hudOpacity;
    boolean hudShowWhenEmpty;
    boolean hudVisible;
    String hudProjectSource;
    boolean previewUseCustom;
    ModConfig.HudHorizontalAnchor previewHorizontalAnchor;
    ModConfig.HudVerticalAnchor previewVerticalAnchor;
    int previewHorizontalMargin;
    int previewVerticalMargin;
    int previewHudX;
    int previewHudY;

    /**
     * 创建 HUD 配置页草稿状态。
     * 字段由调用方在加载配置时填充。
     */
    HudConfigDraft() {
    }
}
