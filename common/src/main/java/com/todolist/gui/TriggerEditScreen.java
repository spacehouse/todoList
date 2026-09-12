package com.todolist.gui;

import com.todolist.client.TriggerTargetSupport;
import com.todolist.task.TaskTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 任务触发器编辑界面。
 * 提供触发类型切换、目标资源输入（可打开物品选择器）、目标数量输入，
 * 保存后通过回调交由调用方落库，取消则原样返回父界面。
 */
public class TriggerEditScreen extends Screen {
    /** 全部内置触发类型，按按钮轮换顺序排列。 */
    private static final TaskTrigger.Type[] TYPES = TaskTrigger.Type.values();

    private final Screen parent;
    private final Consumer<TaskTrigger> saveCallback;
    private TaskTrigger.Type type = TaskTrigger.Type.ITEM_COLLECT;
    private String target = "";
    private int count = 1;
    private EditBox targetField;
    private EditBox countField;
    private Button typeButton;
    private Button pickTargetButton;
    private Button saveButton;

    /**
     * 创建触发器编辑界面。
     *
     * @param parent       父界面
     * @param existing     现有触发器，可为 null（新建）
     * @param saveCallback 保存回调，参数为编辑后的触发器
     */
    public TriggerEditScreen(Screen parent, TaskTrigger existing, Consumer<TaskTrigger> saveCallback) {
        super(Component.translatable("gui.todolist.trigger.edit.title"));
        this.parent = parent;
        this.saveCallback = saveCallback;
        if (existing != null) {
            this.type = existing.getType();
            this.target = existing.getTarget() == null ? "" : existing.getTarget();
            this.count = existing.getTargetCount();
        }
    }

    /**
     * 初始化类型按钮、目标输入、数量输入与确认按钮。
     */
    @Override
    protected void init() {
        int w = Math.max(220, Math.min(320, width - 30));
        int h = 202;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        typeButton = Button.builder(typeText(), button -> {
            int next = (indexOfType(type) + 1) % TYPES.length;
            type = TYPES[next];
            button.setMessage(typeText());
            // 切换类型后旧目标不再适用，清空并要求重新选择
            target = "";
            if (targetField != null) {
                targetField.setValue("");
            }
            applyTypeDependentWidgetState();
            refreshSaveState();
        }).bounds(x + 10, y + 40, w - 20, 20).build();
        addRenderableWidget(typeButton);

        targetField = new EditBox(font, x + 10, y + 80, w - 100, 18, Component.translatable("gui.todolist.trigger.edit.target"));
        targetField.setMaxLength(256);
        targetField.setValue(target);
        targetField.setResponder(text -> {
            target = text;
            refreshSaveState();
        });
        addRenderableWidget(targetField);

        pickTargetButton = Button.builder(pickTargetText(), button -> openTargetSelector())
                .bounds(x + w - 84, y + 79, 74, 20).build();
        addRenderableWidget(pickTargetButton);

        countField = new EditBox(font, x + 10, y + 128, 80, 18, Component.translatable("gui.todolist.trigger.edit.count"));
        countField.setMaxLength(9);
        countField.setValue(String.valueOf(count));
        addRenderableWidget(countField);

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.trigger.edit.clear"), button -> {
            if (saveCallback != null) {
                saveCallback.accept(null);
            }
            onClose();
        }).bounds(x + 10, y + 162, 85, 20).build());

        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> save())
                .bounds(x + w - 95, y + 162, 85, 20).build();
        addRenderableWidget(saveButton);

        applyTypeDependentWidgetState();
        refreshSaveState();
        setFocused(targetField);
    }

    /**
     * 按当前触发类型刷新依赖控件状态。
     * 获得进度触发器只会达成一次，因此数量固定为 1 且不可编辑。
     */
    private void applyTypeDependentWidgetState() {
        if (pickTargetButton != null) {
            pickTargetButton.setMessage(pickTargetText());
        }
        if (countField != null) {
            boolean countEditable = type != TaskTrigger.Type.ADVANCEMENT;
            countField.setEditable(countEditable);
            countField.active = countEditable;
            if (!countEditable) {
                count = 1;
                countField.setValue("1");
            }
        }
    }

    /**
     * 生成类型按钮显示文本。
     *
     * @return 本地化类型文本
     */
    private Component typeText() {
        return Component.translatable("gui.todolist.trigger.type." + type.name().toLowerCase(Locale.ROOT));
    }

    /**
     * 把触发类型映射为选择器目标类型。
     *
     * @param value 触发类型
     * @return 选择器目标类型
     */
    static ItemSelectorScreen.Kind selectorKind(TaskTrigger.Type value) {
        switch (value) {
            case BREAK_BLOCK:
                return ItemSelectorScreen.Kind.BLOCK;
            case KILL_ENTITY:
                return ItemSelectorScreen.Kind.ENTITY;
            case ADVANCEMENT:
                return ItemSelectorScreen.Kind.ADVANCEMENT;
            default:
                return ItemSelectorScreen.Kind.ITEM;
        }
    }

    /**
     * 生成目标选择按钮文本，按当前类型区分「选择物品 / 方块 / 实体 / 进度」。
     *
     * @return 本地化按钮文本
     */
    private Component pickTargetText() {
        return Component.translatable("gui.todolist.trigger.edit.pick." + selectorKind(type).name().toLowerCase(Locale.ROOT));
    }

    /**
     * 打开与当前触发类型匹配的目标选择器，选中后回填目标输入框。
     */
    private void openTargetSelector() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new ItemSelectorScreen(this, selectorKind(type), selected -> {
            target = selected;
            if (targetField != null) {
                targetField.setValue(selected);
            }
        }, false));
    }

    /**
     * 返回类型在轮换数组中的序号。
     *
     * @param value 目标类型
     * @return 序号
     */
    private static int indexOfType(TaskTrigger.Type value) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i] == value) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 根据输入有效性刷新保存按钮状态。
     */
    private void refreshSaveState() {
        if (saveButton != null) {
            saveButton.active = !target.trim().isEmpty();
        }
    }

    /**
     * 校验输入并回调保存。
     */
    private void save() {
        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isEmpty()) {
            return;
        }
        if (saveCallback != null) {
            saveCallback.accept(new TaskTrigger(type, normalizedTarget, resolveTargetCount(type, parseCount())));
        }
        onClose();
    }

    /**
     * 计算最终生效的目标数量：获得进度只会达成一次，数量恒为 1；其余类型至少为 1。
     *
     * @param value       触发类型
     * @param parsedCount 数量输入框解析出的值
     * @return 生效的目标数量
     */
    static int resolveTargetCount(TaskTrigger.Type value, int parsedCount) {
        if (value == TaskTrigger.Type.ADVANCEMENT) {
            return 1;
        }
        return Math.max(1, parsedCount);
    }

    /**
     * 解析数量输入，非法输入回退为 1。
     *
     * @return 目标数量
     */
    private int parseCount() {
        try {
            return Integer.parseInt(countField.getValue().trim());
        } catch (NumberFormatException | NullPointerException ignored) {
            return 1;
        }
    }

    /**
     * 目标输入框内回车直接保存。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && targetField != null && targetField.isFocused()) {
            if (saveButton != null && saveButton.active) {
                save();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 关闭并返回父界面。
     */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /**
     * 渲染编辑面板与字段标签。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int w = Math.max(220, Math.min(320, width - 30));
        int h = 202;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        context.fill(x, y, x + w, y + h, 0xE6161616);
        context.renderOutline(x, y, w, h, 0xFF606060);

        context.drawString(font, title, x + 10, y + 10, 0xFFE0B240, false);
        context.drawString(font, Component.translatable("gui.todolist.trigger.edit.type_label"), x + 10, y + 30, 0xFFAAAAAA, false);
        context.drawString(font, Component.translatable("gui.todolist.trigger.edit.target_label"), x + 10, y + 70, 0xFFAAAAAA, false);
        renderResolvedTargetName(context, x, y, w);
        context.drawString(font, Component.translatable("gui.todolist.trigger.edit.count_label"), x + 10, y + 118, 0xFFAAAAAA, false);
        if (countField != null && countField.getValue().trim().isEmpty()) {
            context.drawString(font, Component.translatable("gui.todolist.trigger.edit.count_hint"), x + 96, y + 132, 0xFF777777, false);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 在目标输入框下方渲染当前目标的本地化名称（如「橡木木板」「石器时代」）。
     * 解析失败或名称与资源 ID 相同时不渲染，避免重复信息。
     *
     * @param context 绘制上下文
     * @param x       面板左上角 x
     * @param y       面板左上角 y
     * @param w       面板宽度
     */
    private void renderResolvedTargetName(GuiGraphics context, int x, int y, int w) {
        String trimmed = target == null ? "" : target.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String displayName = TriggerTargetSupport.resolveTargetDisplayName(type, trimmed);
        if (displayName == null || displayName.isEmpty() || displayName.equals(trimmed)) {
            return;
        }
        String text = "↳ " + displayName;
        int textWidth = font.width(text);
        int drawX = x + w - 10 - textWidth;
        if (drawX < x + 10) {
            drawX = x + 10;
        }
        context.drawString(font, text, drawX, y + 101, 0xFF9CDF9C, false);
    }

    /**
     * 返回保存按钮，供同包测试断言可用状态。
     *
     * @return 保存按钮
     */
    Button getSaveButtonForTest() {
        return saveButton;
    }

    /**
     * 返回目标输入框，供同包测试写入目标。
     *
     * @return 目标输入框
     */
    EditBox getTargetFieldForTest() {
        return targetField;
    }
}
