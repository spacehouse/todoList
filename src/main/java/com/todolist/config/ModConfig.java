package com.todolist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.todolist.TodoListMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod configuration
 *
 * Config file: config/todolist.json
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("todolist.json");

    private static ModConfig instance;

    // Configuration options
    private boolean enableHud = true;
    private boolean enableTaskBook = true;
    private boolean enableSoundEffects = true;
    private int maxTasksPerPlayer = 100;
    private boolean autoSave = true;
    private int autoSaveIntervalMinutes = 5;
    private String defaultPriority = "MEDIUM";
    private boolean enableTaskRewards = false;

    // GUI settings
    private GuiConfig gui = new GuiConfig();

    public static class GuiConfig {
        // Main window size
        private int guiWidth = 400;
        private int guiHeight = 400;

        // Task list
        private int taskListHeight = 140;
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
        private int backgroundColor = 0xFF000000;
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
        private int hudMaxHeight = 120;
        private int hudTodoLimit = 5;
        private int hudDoneLimit = 5;
        private boolean hudDefaultExpanded = true;
        private boolean hudUseCustomPosition = false;
        private int hudCustomX = 10;
        private int hudCustomY = 10;
        private boolean hudShowWhenEmpty = false;
    }

    /**
     * Load configuration from file
     */
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                instance = GSON.fromJson(reader, ModConfig.class);
                TodoListMod.LOGGER.info("Loaded configuration from {}", CONFIG_PATH);
                if (instance.gui.guiHeight < 400) {
                    instance.gui.guiHeight = 400;
                    save();
                }
            } catch (IOException e) {
                TodoListMod.LOGGER.error("Failed to load configuration, using defaults", e);
                instance = new ModConfig();
            }
        } else {
            instance = new ModConfig();
            save();
            TodoListMod.LOGGER.info("Created default configuration at {}", CONFIG_PATH);
        }
    }

    /**
     * Save configuration to file
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(instance, writer);
                TodoListMod.LOGGER.info("Saved configuration to {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            TodoListMod.LOGGER.error("Failed to save configuration", e);
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

    public GuiConfig getGui() { return gui; }

    // GUI configuration getters and setters
    public int getGuiWidth() { return gui.guiWidth; }
    public void setGuiWidth(int width) {
        gui.guiWidth = width;
        save();
    }

    public int getGuiHeight() { return gui.guiHeight; }
    public void setGuiHeight(int height) {
        gui.guiHeight = height;
        save();
    }

    public int getTaskListHeight() { return gui.taskListHeight; }
    public void setTaskListHeight(int height) {
        gui.taskListHeight = height;
        save();
    }

    public int getTaskListY() { return gui.taskListY; }
    public void setTaskListY(int y) {
        gui.taskListY = y;
        save();
    }

    public int getTaskItemHeight() { return gui.taskItemHeight; }
    public void setTaskItemHeight(int height) {
        gui.taskItemHeight = height;
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
        gui.padding = padding;
        save();
    }

    public int getElementSpacing() { return gui.elementSpacing; }
    public void setElementSpacing(int spacing) {
        gui.elementSpacing = spacing;
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
        gui.hudWidth = width;
        save();
    }

    public int getHudMaxHeight() { return gui.hudMaxHeight; }
    public void setHudMaxHeight(int height) {
        gui.hudMaxHeight = height;
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
}
