package com.todolist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.todolist.TodoConstants;
import com.todolist.platform.DataPathProvider;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mod configuration
 *
 * Config file: config/todolist.json
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
    private static final int GUI_WIDTH_MIN = 300;
    private static final int GUI_WIDTH_MAX = 1600;
    private static final int GUI_HEIGHT_MIN = 200;
    private static final int GUI_HEIGHT_MAX = 1200;
    private static final int TASK_ITEM_HEIGHT_MIN = 18;
    private static final int TASK_ITEM_HEIGHT_MAX = 48;
    private static final int SIDEBAR_WIDTH_MIN = 90;
    private static final int SIDEBAR_WIDTH_MAX = 360;
    private static final int HUD_WIDTH_MIN = 140;
    private static final int HUD_WIDTH_MAX = 640;
    private static final int HUD_HEIGHT_MIN = 120;
    private static final int HUD_HEIGHT_MAX = 1000;
    private static final int PADDING_MIN = 4;
    private static final int PADDING_MAX = 24;
    private static final int ELEMENT_SPACING_MIN = 2;
    private static final int ELEMENT_SPACING_MAX = 16;

    private static ModConfig instance;

    /**
     * 服务端命令开放级别：
     * - OP_ONLY：仅 OP 可用（默认）。
     * - VIEW_ONLY：普通玩家仅可使用查看类命令，编辑类仍需 OP。
     * - FULL：普通玩家可使用查看/编辑类命令（不建议公共服务器开启）。
     */
    public enum CommandAccessMode {
        OP_ONLY,
        VIEW_ONLY,
        FULL
    }

    // Configuration options
    private boolean enableHud = true;
    private boolean enableTaskBook = true;
    private boolean enableSoundEffects = true;
    private int maxTasksPerPlayer = 100;
    private boolean autoSave = true;
    private int autoSaveIntervalMinutes = 5;
    private String defaultPriority = "MEDIUM";
    private boolean enableTaskRewards = false;
    private boolean defaultPersonalProjectInitialized = false;
    private CommandAccessMode commandAccessMode = CommandAccessMode.OP_ONLY;

    // GUI settings
    private GuiConfig gui = new GuiConfig();

    public static class GuiConfig {
        // Main window size
        private int guiWidth = 500;
        private int guiHeight = 400;

        // Task list
        private int taskListY = 30;
        private int taskItemHeight = 25;

        // Input fields
        private int inputFieldYOffset = -50; // Offset from bottom
        private int priorityFieldWidth = 100;
        private int titleFieldWidth = 200;
        private int descFieldWidth = 280;

        // Buttons
        private int buttonHeight = 20;
        private int saveButtonYOffset = -30;
        private int completeButtonYOffset = -85;

        // Colors
        private int backgroundColor = 0x88000000;
        private int selectedBackgroundColor = 0xFF555555;
        private int hoveredBackgroundColor = 0xFF333333;
        private int borderColor = 0xFF3F3F3F;

        // Spacing
        private int padding = 10;
        private int elementSpacing = 5;
        private int buttonSpacing = 5;

        // Display options
        private boolean enableDarkTheme = false;
        private boolean showCompletedTasks = true;
        private boolean sortByPriority = false;
        private String hudPosition = "TOP_RIGHT";
        private int hudWidth = 200;
        private int hudMaxHeight = 400;
        private int hudTodoLimit = 5;
        private int hudDoneLimit = 5;
        private boolean hudDefaultExpanded = true;
        private Double hudOpacity = 0.85;
        private boolean hudUseCustomPosition = false;
        private int hudCustomX = 10;
        private int hudCustomY = 10;
        private boolean hudShowWhenEmpty = false;
        private String hudDefaultView = "PERSONAL";
        private String hudProjectSource = "ALL";
        private List<String> hudStarredProjectIds = new ArrayList<>();
        
        // Project Sidebar
        private int projectSidebarWidth = 100;
    }

    /**
     * Load configuration from file
     */
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                instance = GSON.fromJson(reader, ModConfig.class);
                TodoConstants.LOGGER.info("Loaded configuration from {}", CONFIG_PATH);
                boolean changed = instance == null || instance.normalize();
                if (instance == null) {
                    instance = new ModConfig();
                    changed = true;
                }
                if (instance.gui.guiHeight < 400) {
                    instance.gui.guiHeight = 400;
                    changed = true;
                }
                if (changed) {
                    save();
                }
            } catch (IOException e) {
                TodoConstants.LOGGER.error("Failed to load configuration, using defaults", e);
                instance = new ModConfig();
            }
        } else {
            instance = new ModConfig();
            save();
            TodoConstants.LOGGER.info("Created default configuration at {}", CONFIG_PATH);
        }
    }

    private boolean normalize() {
        boolean changed = false;
        if (gui == null) {
            gui = new GuiConfig();
            return true;
        }
        if (commandAccessMode == null) {
            commandAccessMode = CommandAccessMode.OP_ONLY;
            changed = true;
        }
        if (gui.backgroundColor == 0xFF000000) {
            gui.backgroundColor = 0x88000000;
            changed = true;
        }
        if (gui.hudDefaultView == null || gui.hudDefaultView.isEmpty()) {
            gui.hudDefaultView = "PERSONAL";
            changed = true;
        }
        if (gui.hudProjectSource == null || gui.hudProjectSource.isEmpty()) {
            gui.hudProjectSource = "ALL";
            changed = true;
        }
        if (gui.hudStarredProjectIds == null) {
            gui.hudStarredProjectIds = new ArrayList<>();
            changed = true;
        }
        if (gui.hudOpacity == null) {
            gui.hudOpacity = 0.85;
            changed = true;
        } else {
            double clamped = Math.max(0.0, Math.min(1.0, gui.hudOpacity));
            double stepped = Math.round(clamped * 10.0) / 10.0;
            if (Double.compare(stepped, gui.hudOpacity) != 0) {
                gui.hudOpacity = stepped;
                changed = true;
            }
        }
        int normalizedGuiWidth = clamp(gui.guiWidth, GUI_WIDTH_MIN, GUI_WIDTH_MAX);
        if (normalizedGuiWidth != gui.guiWidth) {
            gui.guiWidth = normalizedGuiWidth;
            changed = true;
        }
        int normalizedGuiHeight = clamp(gui.guiHeight, GUI_HEIGHT_MIN, GUI_HEIGHT_MAX);
        if (normalizedGuiHeight != gui.guiHeight) {
            gui.guiHeight = normalizedGuiHeight;
            changed = true;
        }
        int normalizedTaskItemHeight = clamp(gui.taskItemHeight, TASK_ITEM_HEIGHT_MIN, TASK_ITEM_HEIGHT_MAX);
        if (normalizedTaskItemHeight != gui.taskItemHeight) {
            gui.taskItemHeight = normalizedTaskItemHeight;
            changed = true;
        }
        int normalizedSidebarWidth = clamp(gui.projectSidebarWidth, SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX);
        if (normalizedSidebarWidth != gui.projectSidebarWidth) {
            gui.projectSidebarWidth = normalizedSidebarWidth;
            changed = true;
        }
        int normalizedHudWidth = clamp(gui.hudWidth, HUD_WIDTH_MIN, HUD_WIDTH_MAX);
        if (normalizedHudWidth != gui.hudWidth) {
            gui.hudWidth = normalizedHudWidth;
            changed = true;
        }
        int normalizedHudHeight = clamp(gui.hudMaxHeight, HUD_HEIGHT_MIN, HUD_HEIGHT_MAX);
        if (normalizedHudHeight != gui.hudMaxHeight) {
            gui.hudMaxHeight = normalizedHudHeight;
            changed = true;
        }
        int normalizedPadding = clamp(gui.padding, PADDING_MIN, PADDING_MAX);
        if (normalizedPadding != gui.padding) {
            gui.padding = normalizedPadding;
            changed = true;
        }
        int normalizedSpacing = clamp(gui.elementSpacing, ELEMENT_SPACING_MIN, ELEMENT_SPACING_MAX);
        if (normalizedSpacing != gui.elementSpacing) {
            gui.elementSpacing = normalizedSpacing;
            changed = true;
        }
        return changed;
    }

    /**
     * Save configuration to file
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(instance, writer);
                TodoConstants.LOGGER.info("Saved configuration to {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save configuration", e);
        }
    }

    /**
     * Get configuration instance
     */
    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    // Getters and Setters

    public boolean isEnableHud() { return enableHud; }
    public void setEnableHud(boolean enableHud) {
        this.enableHud = enableHud;
        save();
    }

    public boolean isEnableTaskBook() { return enableTaskBook; }
    public void setEnableTaskBook(boolean enableTaskBook) {
        this.enableTaskBook = enableTaskBook;
        save();
    }

    public boolean isEnableSoundEffects() { return enableSoundEffects; }
    public void setEnableSoundEffects(boolean enableSoundEffects) {
        this.enableSoundEffects = enableSoundEffects;
        save();
    }

    public int getMaxTasksPerPlayer() { return maxTasksPerPlayer; }
    public void setMaxTasksPerPlayer(int maxTasksPerPlayer) {
        this.maxTasksPerPlayer = maxTasksPerPlayer;
        save();
    }

    public boolean isAutoSave() { return autoSave; }
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
        save();
    }

    public int getAutoSaveIntervalMinutes() { return autoSaveIntervalMinutes; }
    public void setAutoSaveIntervalMinutes(int autoSaveIntervalMinutes) {
        this.autoSaveIntervalMinutes = autoSaveIntervalMinutes;
        save();
    }

    public String getDefaultPriority() { return defaultPriority; }
    public void setDefaultPriority(String defaultPriority) {
        this.defaultPriority = defaultPriority;
        save();
    }

    public boolean isEnableTaskRewards() { return enableTaskRewards; }
    public void setEnableTaskRewards(boolean enableTaskRewards) {
        this.enableTaskRewards = enableTaskRewards;
        save();
    }

    public boolean isDefaultPersonalProjectInitialized() { return defaultPersonalProjectInitialized; }
    public void setDefaultPersonalProjectInitialized(boolean defaultPersonalProjectInitialized) {
        this.defaultPersonalProjectInitialized = defaultPersonalProjectInitialized;
        save();
    }

    /**
     * 获取命令开放级别配置。
     */
    public CommandAccessMode getCommandAccessMode() {
        return commandAccessMode == null ? CommandAccessMode.OP_ONLY : commandAccessMode;
    }

    /**
     * 设置命令开放级别配置并立即写盘。
     */
    public void setCommandAccessMode(CommandAccessMode commandAccessMode) {
        this.commandAccessMode = commandAccessMode == null ? CommandAccessMode.OP_ONLY : commandAccessMode;
        save();
    }

    public GuiConfig getGui() { return gui; }

    // GUI configuration getters and setters
    public int getGuiWidth() { return gui.guiWidth; }
    public void setGuiWidth(int width) {
        gui.guiWidth = clamp(width, GUI_WIDTH_MIN, GUI_WIDTH_MAX);
        save();
    }

    public int getGuiHeight() { return gui.guiHeight; }
    public void setGuiHeight(int height) {
        gui.guiHeight = clamp(height, GUI_HEIGHT_MIN, GUI_HEIGHT_MAX);
        save();
    }

    public int getTaskListY() { return gui.taskListY; }
    public void setTaskListY(int y) {
        gui.taskListY = y;
        save();
    }

    public int getTaskItemHeight() { return gui.taskItemHeight; }
    public void setTaskItemHeight(int height) {
        gui.taskItemHeight = clamp(height, TASK_ITEM_HEIGHT_MIN, TASK_ITEM_HEIGHT_MAX);
        save();
    }

    public int getInputFieldYOffset() { return gui.inputFieldYOffset; }
    public void setInputFieldYOffset(int offset) {
        gui.inputFieldYOffset = offset;
        save();
    }

    public int getPriorityFieldWidth() { return gui.priorityFieldWidth; }
    public void setPriorityFieldWidth(int width) {
        gui.priorityFieldWidth = width;
        save();
    }

    public int getTitleFieldWidth() { return gui.titleFieldWidth; }
    public void setTitleFieldWidth(int width) {
        gui.titleFieldWidth = width;
        save();
    }

    public int getDescFieldWidth() { return gui.descFieldWidth; }
    public void setDescFieldWidth(int width) {
        gui.descFieldWidth = width;
        save();
    }

    public int getButtonHeight() { return gui.buttonHeight; }
    public void setButtonHeight(int height) {
        gui.buttonHeight = height;
        save();
    }

    public int getSaveButtonYOffset() { return gui.saveButtonYOffset; }
    public void setSaveButtonYOffset(int offset) {
        gui.saveButtonYOffset = offset;
        save();
    }

    public int getCompleteButtonYOffset() { return gui.completeButtonYOffset; }
    public void setCompleteButtonYOffset(int offset) {
        gui.completeButtonYOffset = offset;
        save();
    }

    public int getBackgroundColor() { return gui.backgroundColor; }
    public void setBackgroundColor(int color) {
        gui.backgroundColor = color;
        save();
    }

    public int getSelectedBackgroundColor() { return gui.selectedBackgroundColor; }
    public void setSelectedBackgroundColor(int color) {
        gui.selectedBackgroundColor = color;
        save();
    }

    public int getHoveredBackgroundColor() { return gui.hoveredBackgroundColor; }
    public void setHoveredBackgroundColor(int color) {
        gui.hoveredBackgroundColor = color;
        save();
    }

    public int getBorderColor() { return gui.borderColor; }
    public void setBorderColor(int color) {
        gui.borderColor = color;
        save();
    }

    public int getPadding() { return gui.padding; }
    public void setPadding(int padding) {
        gui.padding = clamp(padding, PADDING_MIN, PADDING_MAX);
        save();
    }

    public int getElementSpacing() { return gui.elementSpacing; }
    public void setElementSpacing(int spacing) {
        gui.elementSpacing = clamp(spacing, ELEMENT_SPACING_MIN, ELEMENT_SPACING_MAX);
        save();
    }

    public int getButtonSpacing() { return gui.buttonSpacing; }
    public void setButtonSpacing(int spacing) {
        gui.buttonSpacing = spacing;
        save();
    }

    public boolean isEnableDarkTheme() { return gui.enableDarkTheme; }
    public void setEnableDarkTheme(boolean enableDarkTheme) {
        gui.enableDarkTheme = enableDarkTheme;
        save();
    }

    public String getHudPosition() { return gui.hudPosition; }
    public void setHudPosition(String position) {
        gui.hudPosition = position;
        save();
    }

    public boolean isShowCompletedTasks() { return gui.showCompletedTasks; }
    public void setShowCompletedTasks(boolean show) {
        gui.showCompletedTasks = show;
        save();
    }

    public boolean isSortByPriority() { return gui.sortByPriority; }
    public void setSortByPriority(boolean sort) {
        gui.sortByPriority = sort;
        save();
    }

    public int getHudWidth() { return gui.hudWidth; }
    public void setHudWidth(int width) {
        gui.hudWidth = clamp(width, HUD_WIDTH_MIN, HUD_WIDTH_MAX);
        save();
    }

    public int getHudMaxHeight() { return gui.hudMaxHeight; }
    public void setHudMaxHeight(int height) {
        gui.hudMaxHeight = clamp(height, HUD_HEIGHT_MIN, HUD_HEIGHT_MAX);
        save();
    }

    public int getHudTodoLimit() { return gui.hudTodoLimit; }
    public void setHudTodoLimit(int limit) {
        gui.hudTodoLimit = limit;
        save();
    }

    public int getHudDoneLimit() { return gui.hudDoneLimit; }
    public void setHudDoneLimit(int limit) {
        gui.hudDoneLimit = limit;
        save();
    }

    public boolean isHudDefaultExpanded() { return gui.hudDefaultExpanded; }
    public void setHudDefaultExpanded(boolean expanded) {
        gui.hudDefaultExpanded = expanded;
        save();
    }

    public double getHudOpacity() {
        if (gui.hudOpacity == null) {
            gui.hudOpacity = 0.85;
        }
        double v = gui.hudOpacity;
        if (v < 0.0) v = 0.0;
        if (v > 1.0) v = 1.0;
        return v;
    }

    public void setHudOpacity(double opacity) {
        double clamped = Math.max(0.0, Math.min(1.0, opacity));
        double stepped = Math.round(clamped * 10.0) / 10.0;
        gui.hudOpacity = stepped;
        save();
    }

    public boolean isHudUseCustomPosition() { return gui.hudUseCustomPosition; }
    public void setHudUseCustomPosition(boolean useCustom) {
        gui.hudUseCustomPosition = useCustom;
        save();
    }

    public int getHudCustomX() { return gui.hudCustomX; }
    public void setHudCustomX(int x) {
        gui.hudCustomX = x;
        save();
    }

    public int getHudCustomY() { return gui.hudCustomY; }
    public void setHudCustomY(int y) {
        gui.hudCustomY = y;
        save();
    }

    public boolean isHudShowWhenEmpty() { return gui.hudShowWhenEmpty; }
    public void setHudShowWhenEmpty(boolean show) {
        gui.hudShowWhenEmpty = show;
        save();
    }

    public String getHudDefaultView() {
        if (gui.hudDefaultView == null || gui.hudDefaultView.isEmpty()) {
            gui.hudDefaultView = "PERSONAL";
        }
        return gui.hudDefaultView;
    }

    public void setHudDefaultView(String view) {
        if (view == null || view.isEmpty()) {
            return;
        }
        gui.hudDefaultView = view;
        save();
    }

    public String getHudProjectSource() {
        if (gui.hudProjectSource == null || gui.hudProjectSource.isEmpty()) {
            gui.hudProjectSource = "ALL";
        }
        return gui.hudProjectSource;
    }

    public void setHudProjectSource(String source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        gui.hudProjectSource = source;
        save();
    }

    public List<String> getHudStarredProjectIds() {
        if (gui.hudStarredProjectIds == null) {
            gui.hudStarredProjectIds = new ArrayList<>();
        }
        return new ArrayList<>(gui.hudStarredProjectIds);
    }

    public boolean isHudProjectStarred(String projectId) {
        if (projectId == null || projectId.isEmpty()) return false;
        if (gui.hudStarredProjectIds == null) {
            gui.hudStarredProjectIds = new ArrayList<>();
        }
        return gui.hudStarredProjectIds.contains(projectId);
    }

    public void toggleHudStarredProjectId(String projectId) {
        if (projectId == null || projectId.isEmpty()) return;
        if (gui.hudStarredProjectIds == null) {
            gui.hudStarredProjectIds = new ArrayList<>();
        }
        if (gui.hudStarredProjectIds.contains(projectId)) {
            gui.hudStarredProjectIds.remove(projectId);
        } else {
            gui.hudStarredProjectIds.add(projectId);
        }
        save();
    }

    public int getProjectSidebarWidth() { return gui.projectSidebarWidth; }
    public void setProjectSidebarWidth(int width) {
        gui.projectSidebarWidth = clamp(width, SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX);
        save();
    }
    
    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}


