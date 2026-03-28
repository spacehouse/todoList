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
 * 新建项目界面：输入项目名称并选择个人/团队范围后发送创建请求。
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
     * 创建新建项目界面，默认范围为个人项目。
     */
    public AddProjectScreen(Screen parent) {
        this(parent, null);
    }

    /**
     * 创建新建项目界面，并可指定默认范围。
     */
    public AddProjectScreen(Screen parent, Project.Scope defaultScope) {
        super(Component.translatable("gui.todolist.add_project.title"));
        this.parent = parent;
        if (defaultScope != null) {
            this.scope = defaultScope;
        }
    }

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

        // Name Field
        nameField = new EditBox(font, x + 10, y + 35, w - 20, 20, Component.translatable("gui.todolist.project.name"));
        nameField.setMaxLength(32);
        addRenderableWidget(nameField);

        // Scope Toggle
        scopeButton = Button.builder(getScopeText(), button -> {
            if (!teamProjectsEnabled) return;
            scope = (scope == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            button.setMessage(getScopeText());
        }).bounds(x + 10, y + 65, w - 20, 20).build();
        scopeButton.active = teamProjectsEnabled;
        addRenderableWidget(scopeButton);

        // Create Button
        createButton = Button.builder(Component.translatable("gui.todolist.create"), button -> createProject())
                .bounds(x + 10, y + 110, 85, 20).build();
        addRenderableWidget(createButton);

        // Cancel Button
        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + w - 95, y + 110, 85, 20).build();
        addRenderableWidget(cancelButton);
        
        setFocused(nameField);
    }

    private Component getScopeText() {
        return Component.translatable("gui.todolist.scope", Component.translatable("gui.todolist.scope." + scope.name().toLowerCase()));
    }

    /**
     * 返回名称输入框，供同包测试代码直接写入名称。
     *
     * @return 名称输入框
     */
    EditBox getNameFieldForTest() {
        return nameField;
    }

    /**
     * 返回项目范围切换按钮，供同包测试代码触发点击。
     *
     * @return 范围切换按钮
     */
    Button getScopeButtonForTest() {
        return scopeButton;
    }

    /**
     * 返回创建按钮，供同包测试代码直接触发创建流程。
     *
     * @return 创建按钮
     */
    Button getCreateButtonForTest() {
        return createButton;
    }

    /**
     * 返回取消按钮，供同包测试代码直接触发关闭流程。
     *
     * @return 取消按钮
     */
    Button getCancelButtonForTest() {
        return cancelButton;
    }

    /**
     * 返回当前项目范围，供同包测试代码断言切换结果。
     *
     * @return 当前项目范围
     */
    Project.Scope getScopeForTest() {
        return scope;
    }

    /**
     * 返回当前是否启用团队项目能力，供同包测试代码断言界面约束。
     *
     * @return 团队项目能力开关
     */
    boolean isTeamProjectsEnabledForTest() {
        return teamProjectsEnabled;
    }

    private void createProject() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;

        Project project = new Project();
        project.setName(name);
        project.setScope(teamProjectsEnabled ? scope : Project.Scope.PERSONAL);
        // Owner UUID is set by server, but we can set it here for local preview or strictness
        if (minecraft.player != null) {
            project.setOwnerUuid(minecraft.player.getUUID().toString());
        }
        
        // Random pastel color
        float hue = random.nextFloat();
        float saturation = 0.5f + random.nextFloat() * 0.5f;
        float brightness = 0.8f + random.nextFloat() * 0.2f;
        int color = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        project.setColor(color);
        
        ClientBridge.ops().sendAddProject(project);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
    
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


