package com.todolist.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * 26.x 屏幕文本输入修复：恢复 init 预聚焦输入框的系统输入法（中文 IME）可用性。
 *
 * <p>Minecraft 26.1 起 {@code setScreen} 在 {@code Screen.init} 完成后会无条件
 * {@code TextInputManager.stopTextInput()}，导致 init 中预聚焦的 EditBox 虽然持有焦点、
 * 却无法唤起系统输入法，直到用户再次点击输入框（1.21.x 无此行为，属 26.x 断点回归）。
 * {@code EditBox.setFocused} 每次调用都会无条件派发
 * {@code Minecraft.onTextInputFocusChange}（无变化守卫），因此屏幕首帧 tick 时对
 * 预聚焦输入框重发一次聚焦事件即可把文本输入重新拉起。
 *
 * <p>使用方式：init 完成预聚焦后调用 {@link #armAfterInit()}，并在
 * {@code tick()} 覆写中调用 {@link #tickAfterInit(Screen, EditBox)}。
 */
final class ScreenTextInputSupport {

    /** 是否有待重新拉起的预聚焦输入框（仅首帧消费一次）。 */
    private boolean reassertPending;

    /**
     * init 完成预聚焦后调用，标记首帧需要重新拉起文本输入。
     */
    void armAfterInit() {
        this.reassertPending = true;
    }

    /**
     * 每帧 tick 调用：首帧对预聚焦输入框重发聚焦事件。
     *
     * <p>带焦点一致性守卫：仅当输入框仍是屏幕当前焦点时重发，
     * 避免覆盖首帧内用户已完成的其他焦点操作（如 canEdit=false 时未聚焦）。
     *
     * @param screen       当前屏幕
     * @param focusedField init 中预聚焦的输入框，可为 null
     */
    void tickAfterInit(Screen screen, EditBox focusedField) {
        if (!reassertPending) {
            return;
        }
        reassertPending = false;
        if (focusedField != null && screen.getFocused() == focusedField) {
            focusedField.setFocused(true);
        }
    }
}
