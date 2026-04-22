package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import org.lwjgl.glfw.GLFW;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 新建项目界面，负责输入项目名称、切换项目范围并提交创建请求。
 */
public class AddProjectScreen extends Screen {
    private final Screen parent;
    private EditBox nameField;
    private Project.Scope scope = Project.Scope.PERSONAL;
    private Button scopeButton;
    private Button createButton;
    private Button cancelButton;
    private boolean teamProjectsEnabled = true;
    private final Random random = new Random();

    /**
     * 创建新建项目界面，默认使用当前默认范围。
     */
    public AddProjectScreen(Screen parent) {
        this(parent, null);
    }

    /**
     * 创建新建项目界面，并允许指定默认项目范围。
     */
    public AddProjectScreen(Screen parent, Project.Scope defaultScope) {
        super(Component.translatable("gui.todolist.add_project.title"));
        this.parent = parent;
        if (defaultScope != null) {
            this.scope = defaultScope;
        }
    }

    /**
     * 初始化输入框、范围切换按钮和确认按钮。
     */
    @Override
    protected void init() {
        teamProjectsEnabled = ClientBridge.ops().isTeamProjectsEnabled();
        if (!teamProjectsEnabled) {
            scope = Project.Scope.PERSONAL;
        }

        int w = Math.max(180, Math.min(320, width - 20));
        int h = Math.max(140, Math.min(180, height - 20));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        nameField = new EditBox(font, x + 10, y + 35, w - 20, 20, Component.translatable("gui.todolist.project.name"));
        nameField.setMaxLength(32);
        addRenderableWidget(nameField);

        scopeButton = Button.builder(getScopeText(), button -> {
            if (!teamProjectsEnabled) {
                return;
            }
            scope = (scope == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            button.setMessage(getScopeText());
        }).bounds(x + 10, y + 65, w - 20, 20).build();
        scopeButton.active = teamProjectsEnabled;
        addRenderableWidget(scopeButton);

        createButton = Button.builder(Component.translatable("gui.todolist.create"), button -> createProject())
                .bounds(x + 10, y + 110, 85, 20).build();
        addRenderableWidget(createButton);

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + w - 95, y + 110, 85, 20).build();
        addRenderableWidget(cancelButton);

        setFocused(nameField);
    }

    /**
     * 生成当前范围按钮的显示文本。
     */
    private Component getScopeText() {
        return Component.translatable("gui.todolist.scope",
                Component.translatable("gui.todolist.scope." + scope.name().toLowerCase()));
    }

    /**
     * 返回名称输入框，供同包测试直接写入名称。
     *
     * @return 名称输入框
     */
    EditBox getNameFieldForTest() {
        return nameField;
    }

    /**
     * 返回项目范围切换按钮，供同包测试触发点击。
     *
     * @return 范围切换按钮
     */
    Button getScopeButtonForTest() {
        return scopeButton;
    }

    /**
     * 返回创建按钮，供同包测试直接触发创建流程。
     *
     * @return 创建按钮
     */
    Button getCreateButtonForTest() {
        return createButton;
    }

    /**
     * 返回取消按钮，供同包测试直接触发关闭流程。
     *
     * @return 取消按钮
     */
    Button getCancelButtonForTest() {
        return cancelButton;
    }

    /**
     * 返回当前项目范围，供同包测试断言切换结果。
     *
     * @return 当前项目范围
     */
    Project.Scope getScopeForTest() {
        return scope;
    }

    /**
     * 按当前输入创建项目并发送到客户端桥接层。
     */
    private void createProject() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }

        Project project = new Project();
        project.setName(name);
        project.setScope(teamProjectsEnabled ? scope : Project.Scope.PERSONAL);
        if (minecraft.player != null) {
            project.setOwnerUuid(minecraft.player.getUUID().toString());
        }

        float hue = random.nextFloat();
        float saturation = 0.5f + random.nextFloat() * 0.5f;
        float brightness = 0.8f + random.nextFloat() * 0.2f;
        int color = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        project.setColor(color);

        ClientBridge.ops().sendAddProject(project);
        onClose();
    }

    /**
     * 关闭界面并返回父界面。
     */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /**
     * 处理回车快捷创建逻辑。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (nameField.isFocused()) {
                createProject();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 渲染新建项目弹窗与标题文本。
     */
    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background is drawn manually in render to keep cross-loader consistency.
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());
        int w = Math.max(180, Math.min(320, width - 20));
        int h = Math.max(140, Math.min(180, height - 20));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        context.fill(x, y, x + w, y + h, 0xFF202020);
        context.renderOutline(x, y, w, h, 0xFFFFFFFF);

        context.drawString(font, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawString(font, Component.translatable("gui.todolist.label.name"), x + 10, y + 25, 0xFFAAAAAA, false);

        super.render(context, mouseX, mouseY, delta);
    }
}
