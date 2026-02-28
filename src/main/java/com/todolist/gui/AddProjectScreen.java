package com.todolist.gui;

import com.todolist.client.ClientProjectPackets;
import com.todolist.client.TodoClient;
import com.todolist.project.Project;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class AddProjectScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private Project.Scope scope = Project.Scope.PERSONAL;
    private ButtonWidget scopeButton;
    private boolean teamProjectsEnabled = true;
    private final Random random = new Random();

    public AddProjectScreen(Screen parent) {
        this(parent, null);
    }

    public AddProjectScreen(Screen parent, Project.Scope defaultScope) {
        super(Text.translatable("gui.todolist.add_project.title"));
        this.parent = parent;
        if (defaultScope != null) {
            this.scope = defaultScope;
        }
    }

    @Override
    protected void init() {
        teamProjectsEnabled = TodoClient.isTeamProjectsEnabled();
        if (!teamProjectsEnabled) {
            scope = Project.Scope.PERSONAL;
        }

        int w = 200;
        int h = 150;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        // Name Field
        nameField = new TextFieldWidget(textRenderer, x + 10, y + 35, w - 20, 20, Text.translatable("gui.todolist.project.name"));
        nameField.setMaxLength(32);
        addDrawableChild(nameField);

        // Scope Toggle
        scopeButton = ButtonWidget.builder(getScopeText(), button -> {
            if (!teamProjectsEnabled) return;
            scope = (scope == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            button.setMessage(getScopeText());
        }).dimensions(x + 10, y + 65, w - 20, 20).build();
        scopeButton.active = teamProjectsEnabled;
        addDrawableChild(scopeButton);

        // Create Button
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.todolist.create"), button -> createProject())
                .dimensions(x + 10, y + 110, 85, 20).build());

        // Cancel Button
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), button -> close())
                .dimensions(x + w - 95, y + 110, 85, 20).build());
        
        setFocused(nameField);
    }

    private Text getScopeText() {
        return Text.translatable("gui.todolist.scope", Text.translatable("gui.todolist.scope." + scope.name().toLowerCase()));
    }

    private void createProject() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        Project project = new Project();
        project.setName(name);
        project.setScope(teamProjectsEnabled ? scope : Project.Scope.PERSONAL);
        // Owner UUID is set by server, but we can set it here for local preview or strictness
        if (client.player != null) {
            project.setOwnerUuid(client.player.getUuid().toString());
        }
        
        // Random pastel color
        float hue = random.nextFloat();
        float saturation = 0.5f + random.nextFloat() * 0.5f;
        float brightness = 0.8f + random.nextFloat() * 0.2f;
        int color = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        project.setColor(color);
        
        ClientProjectPackets.sendAddProject(project);
        close();
    }

    @Override
    public void close() {
        client.setScreen(parent);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        
        int w = 200;
        int h = 150;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        
        context.fill(x, y, x + w, y + h, 0xFF202020);
        context.drawBorder(x, y, w, h, 0xFFFFFFFF);
        
        context.drawText(textRenderer, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.translatable("gui.todolist.label.name"), x + 10, y + 25, 0xFFAAAAAA, false);
        
        super.render(context, mouseX, mouseY, delta);
    }
}
