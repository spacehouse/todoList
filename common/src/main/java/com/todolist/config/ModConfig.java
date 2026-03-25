package com.todolist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.todolist.TodoConstants;
import com.todolist.client.ClientBridge;
import com.todolist.platform.DataPathProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Mod configuration
 *
 * Config file: config/todolist.json
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
    private static final String COMMAND_ACCESS_MODE_KEY = "\"commandAccessMode\"";
    private static final String LANG_ASSET_DIR = "assets/todolist/lang/";
    private static final String LANG_ZH_CN = "zh_cn";
    private static final String LANG_EN_US = "en_us";
    private static final String[] COMMAND_ACCESS_MODE_COMMENT_KEYS = new String[] {
            "config.todolist.command_access_mode.comment.title",
            "config.todolist.command_access_mode.comment.op_only",
            "config.todolist.command_access_mode.comment.view_only",
            "config.todolist.command_access_mode.comment.full"
    };
    private static final String[] COMMAND_ACCESS_MODE_COMMENT_FALLBACK_EN = new String[] {
            "commandAccessMode notes:",
            "OP_ONLY: only OP can use all commands",
            "VIEW_ONLY: non-OP can use view commands, edit still requires OP",
            "FULL: non-OP can use view/edit commands (not recommended for public servers)"
    };
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
    private static final int HUD_DEFAULT_MARGIN = 10;
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
    // 配置项：commandAccessMode（服务端命令权限）
    // OP_ONLY：仅 OP 可使用所有命令
    // VIEW_ONLY：普通玩家仅可使用查看类命令，编辑类命令仍需 OP
    // FULL：普通玩家可使用查看/编辑类命令（不建议公共服务器）
    private CommandAccessMode commandAccessMode = CommandAccessMode.OP_ONLY;
    private transient boolean commandAccessModeDirty;

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
        private Double hudCustomXRatio;
        private Double hudCustomYRatio;
        private HudHorizontalAnchor hudCustomHorizontalAnchor;
        private HudVerticalAnchor hudCustomVerticalAnchor;
        private Integer hudCustomHorizontalMargin;
        private Integer hudCustomVerticalMargin;
        private boolean hudShowWhenEmpty = false;
        private String hudDefaultView = "PERSONAL";
        private String hudProjectSource = "ALL";
        private List<String> hudStarredProjectIds = new ArrayList<>();
        private Map<String, String> lastActiveProjectIdsByNamespace = new HashMap<>();
        
        // Project Sidebar
        private int projectSidebarWidth = 100;
    }

    /**
     * HUD 水平方向锚点。
     */
    public enum HudHorizontalAnchor {
        LEFT,
        RIGHT
    }

    /**
     * HUD 垂直方向锚点。
     */
    public enum HudVerticalAnchor {
        TOP,
        BOTTOM
    }

    /**
     * HUD 最终落点信息。
     */
    public static class HudPlacement {
        private final int x;
        private final int y;

        /**
         * 创建 HUD 落点对象。
         *
         * @param x HUD 左上角 X 坐标
         * @param y HUD 左上角 Y 坐标
         */
        public HudPlacement(int x, int y) {
            this.x = x;
            this.y = y;
        }

        /**
         * 获取 HUD 左上角 X 坐标。
         *
         * @return HUD X 坐标
         */
        public int getX() {
            return x;
        }

        /**
         * 获取 HUD 左上角 Y 坐标。
         *
         * @return HUD Y 坐标
         */
        public int getY() {
            return y;
        }
    }

    /**
     * Load configuration from file
     */
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(CONFIG_PATH), StandardCharsets.UTF_8)) {
                JsonReader jsonReader = new JsonReader(reader);
                jsonReader.setLenient(true);
                instance = GSON.fromJson(jsonReader, ModConfig.class);
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
                instance.commandAccessModeDirty = false;
            } catch (IOException e) {
                TodoConstants.LOGGER.error("Failed to load configuration, using defaults", e);
                instance = new ModConfig();
                instance.commandAccessModeDirty = false;
            }
        } else {
            instance = new ModConfig();
            save();
            instance.commandAccessModeDirty = false;
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
        if (gui.lastActiveProjectIdsByNamespace == null) {
            gui.lastActiveProjectIdsByNamespace = new HashMap<>();
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
        if (gui.hudCustomXRatio != null) {
            double normalizedRatio = clampRatio(gui.hudCustomXRatio);
            if (Double.compare(normalizedRatio, gui.hudCustomXRatio) != 0) {
                gui.hudCustomXRatio = normalizedRatio;
                changed = true;
            }
        }
        if (gui.hudCustomYRatio != null) {
            double normalizedRatio = clampRatio(gui.hudCustomYRatio);
            if (Double.compare(normalizedRatio, gui.hudCustomYRatio) != 0) {
                gui.hudCustomYRatio = normalizedRatio;
                changed = true;
            }
        }
        if (gui.hudCustomHorizontalMargin != null && gui.hudCustomHorizontalMargin < 0) {
            gui.hudCustomHorizontalMargin = 0;
            changed = true;
        }
        if (gui.hudCustomVerticalMargin != null && gui.hudCustomVerticalMargin < 0) {
            gui.hudCustomVerticalMargin = 0;
            changed = true;
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
            preserveExternalCommandAccessModeIfNeeded();
            Files.createDirectories(CONFIG_PATH.getParent());
            try (java.io.Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                String json = GSON.toJson(instance);
                writer.write(addCommandAccessModeComment(json));
                TodoConstants.LOGGER.info("Saved configuration to {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save configuration", e);
        }
    }

    /**
     * Preserve a manually edited command access mode from disk when this save is triggered by unrelated settings.
     */
    private static void preserveExternalCommandAccessModeIfNeeded() {
        if (instance == null || instance.commandAccessModeDirty) {
            return;
        }
        CommandAccessMode persistedMode = loadPersistedCommandAccessMode();
        if (persistedMode != null && persistedMode != instance.getCommandAccessMode()) {
            instance.commandAccessMode = persistedMode;
        }
    }

    /**
     * Load only the persisted command access mode from disk without replacing the current in-memory config instance.
     *
     * @return the persisted command access mode, or null when unavailable
     */
    private static CommandAccessMode loadPersistedCommandAccessMode() {
        if (!Files.exists(CONFIG_PATH)) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(CONFIG_PATH), StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            ModConfig persisted = GSON.fromJson(jsonReader, ModConfig.class);
            return persisted == null ? null : persisted.commandAccessMode;
        } catch (Exception e) {
            TodoConstants.LOGGER.warn("Failed to read persisted commandAccessMode from {}", CONFIG_PATH, e);
            return null;
        }
    }

    private static String addCommandAccessModeComment(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String[] comments = buildCommandAccessModeComments();
        String[] lines = json.split("\n", -1);
        StringBuilder builder = new StringBuilder(json.length() + 256);
        boolean inserted = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!inserted && line.contains(COMMAND_ACCESS_MODE_KEY)) {
                int keyIndex = line.indexOf(COMMAND_ACCESS_MODE_KEY);
                String indent = keyIndex <= 0 ? "" : line.substring(0, keyIndex);
                for (String comment : comments) {
                    builder.append(indent).append("// ").append(comment).append("\n");
                }
                inserted = true;
            }
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private static String[] buildCommandAccessModeComments() {
        String langCode = resolveLangCode();
        Map<String, String> primary = loadLangMap(langCode);
        Map<String, String> fallback = LANG_EN_US.equals(langCode) ? primary : loadLangMap(LANG_EN_US);
        String[] comments = new String[COMMAND_ACCESS_MODE_COMMENT_KEYS.length];
        for (int i = 0; i < COMMAND_ACCESS_MODE_COMMENT_KEYS.length; i++) {
            String key = COMMAND_ACCESS_MODE_COMMENT_KEYS[i];
            String fallbackText = i < COMMAND_ACCESS_MODE_COMMENT_FALLBACK_EN.length
                    ? COMMAND_ACCESS_MODE_COMMENT_FALLBACK_EN[i]
                    : key;
            comments[i] = translateWithFallback(primary, fallback, key, fallbackText);
        }
        return comments;
    }

    private static String resolveLangCode() {
        Locale locale = Locale.getDefault();
        String language = locale == null ? "" : locale.getLanguage();
        if (language != null && language.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return LANG_ZH_CN;
        }
        return LANG_EN_US;
    }

    private static Map<String, String> loadLangMap(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return new HashMap<>();
        }
        String resourcePath = LANG_ASSET_DIR + langCode + ".json";
        try (InputStream input = ModConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return new HashMap<>();
            }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> map = GSON.fromJson(reader, type);
                return map == null ? new HashMap<>() : map;
            }
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static String translateWithFallback(Map<String, String> primary, Map<String, String> fallback, String key, String defaultValue) {
        String value = primary == null ? null : primary.get(key);
        if (value == null || value.isBlank()) {
            value = fallback == null ? null : fallback.get(key);
        }
        if (value == null || value.isBlank()) {
            return defaultValue == null ? "" : defaultValue;
        }
        return value;
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
        this.commandAccessModeDirty = true;
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

    /**
     * 设置 HUD 是否使用自定义位置。
     *
     * @param useCustom true 表示启用自定义位置
     */
    public void setHudUseCustomPosition(boolean useCustom) {
        gui.hudUseCustomPosition = useCustom;
        save();
    }

    public int getHudCustomX() { return gui.hudCustomX; }

    /**
     * 设置 HUD 自定义 X 坐标（兼容旧配置的绝对像素值）。
     *
     * @param x HUD 自定义 X 坐标
     */
    public void setHudCustomX(int x) {
        gui.hudCustomX = x;
        save();
    }

    public int getHudCustomY() { return gui.hudCustomY; }

    /**
     * 设置 HUD 自定义 Y 坐标（兼容旧配置的绝对像素值）。
     *
     * @param y HUD 自定义 Y 坐标
     */
    public void setHudCustomY(int y) {
        gui.hudCustomY = y;
        save();
    }

    /**
     * 判断 HUD 是否已持久化比例坐标。
     *
     * @return true 表示存在比例坐标
     */
    public boolean hasHudCustomPositionRatios() {
        return gui.hudCustomXRatio != null && gui.hudCustomYRatio != null;
    }

    /**
     * 判断 HUD 是否已持久化锚点和边距。
     *
     * @return true 表示存在完整锚点信息
     */
    public boolean hasHudCustomAnchors() {
        return gui.hudCustomHorizontalAnchor != null
                && gui.hudCustomVerticalAnchor != null
                && gui.hudCustomHorizontalMargin != null
                && gui.hudCustomVerticalMargin != null;
    }

    /**
     * 获取 HUD 自定义 X 比例坐标。
     *
     * @return 0.0 ~ 1.0 的 X 比例
     */
    public double getHudCustomXRatio() {
        return gui.hudCustomXRatio == null ? 0.0 : clampRatio(gui.hudCustomXRatio);
    }

    /**
     * 获取 HUD 自定义 Y 比例坐标。
     *
     * @return 0.0 ~ 1.0 的 Y 比例
     */
    public double getHudCustomYRatio() {
        return gui.hudCustomYRatio == null ? 0.0 : clampRatio(gui.hudCustomYRatio);
    }

    /**
     * 获取 HUD 水平锚点。
     *
     * @return HUD 水平锚点
     */
    public HudHorizontalAnchor getHudCustomHorizontalAnchor() {
        return gui.hudCustomHorizontalAnchor == null ? HudHorizontalAnchor.RIGHT : gui.hudCustomHorizontalAnchor;
    }

    /**
     * 获取 HUD 垂直锚点。
     *
     * @return HUD 垂直锚点
     */
    public HudVerticalAnchor getHudCustomVerticalAnchor() {
        return gui.hudCustomVerticalAnchor == null ? HudVerticalAnchor.TOP : gui.hudCustomVerticalAnchor;
    }

    /**
     * 获取 HUD 水平边距。
     *
     * @return HUD 水平边距
     */
    public int getHudCustomHorizontalMargin() {
        return gui.hudCustomHorizontalMargin == null ? HUD_DEFAULT_MARGIN : Math.max(0, gui.hudCustomHorizontalMargin);
    }

    /**
     * 获取 HUD 垂直边距。
     *
     * @return HUD 垂直边距
     */
    public int getHudCustomVerticalMargin() {
        return gui.hudCustomVerticalMargin == null ? HUD_DEFAULT_MARGIN : Math.max(0, gui.hudCustomVerticalMargin);
    }

    /**
     * 在仅存在旧版绝对像素坐标或比例坐标时，推导 HUD 锚点和边距。
     *
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @param hudWidth HUD 宽度
     * @param hudHeight HUD 高度
     */
    public void ensureHudCustomAnchors(int screenWidth, int screenHeight, int hudWidth, int hudHeight) {
        if (!gui.hudUseCustomPosition || hasHudCustomAnchors()) {
            return;
        }
        int absoluteX;
        int absoluteY;
        if (hasHudCustomPositionRatios()) {
            absoluteX = resolveHudCoordinate(getHudCustomXRatio(), screenWidth, hudWidth);
            absoluteY = resolveHudCoordinate(getHudCustomYRatio(), screenHeight, hudHeight);
        } else {
            absoluteX = clampHudCoordinate(gui.hudCustomX, screenWidth, hudWidth);
            absoluteY = clampHudCoordinate(gui.hudCustomY, screenHeight, hudHeight);
        }
        updateHudCustomAnchorState(absoluteX, absoluteY, screenWidth, screenHeight, hudWidth, hudHeight);
        gui.hudCustomXRatio = null;
        gui.hudCustomYRatio = null;
    }

    /**
     * 根据当前配置解析 HUD 最终坐标。
     *
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @param hudWidth HUD 宽度
     * @param hudHeight HUD 高度
     * @return HUD 最终落点
     */
    public HudPlacement resolveHudPlacement(int screenWidth, int screenHeight, int hudWidth, int hudHeight) {
        if (!gui.hudUseCustomPosition) {
            return resolveDefaultHudPlacement(screenWidth, screenHeight, hudWidth, hudHeight);
        }
        ensureHudCustomAnchors(screenWidth, screenHeight, hudWidth, hudHeight);
        int resolvedX = resolveHudXByAnchor(screenWidth, hudWidth, getHudCustomHorizontalAnchor(), getHudCustomHorizontalMargin());
        int resolvedY = resolveHudYByAnchor(screenHeight, hudHeight, getHudCustomVerticalAnchor(), getHudCustomVerticalMargin());
        return new HudPlacement(resolvedX, resolvedY);
    }

    /**
     * 使用绝对坐标更新 HUD 自定义位置，并同步刷新兼容像素值与比例值。
     *
     * @param absoluteX HUD 当前绝对 X 坐标
     * @param absoluteY HUD 当前绝对 Y 坐标
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @param hudWidth HUD 宽度
     * @param hudHeight HUD 高度
     */
    public void updateHudCustomPosition(int absoluteX, int absoluteY, int screenWidth, int screenHeight, int hudWidth, int hudHeight) {
        gui.hudUseCustomPosition = true;
        updateHudCustomAnchorState(absoluteX, absoluteY, screenWidth, screenHeight, hudWidth, hudHeight);
        gui.hudCustomXRatio = null;
        gui.hudCustomYRatio = null;
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
        ClientBridge.ops().sendHudStarredProjectIds(getHudStarredProjectIds());
    }

    /**
     * 批量设置 HUD 星标项目列表，并将结果保存到本地配置。
     *
     * @param projectIds 最新星标项目 ID 列表
     */
    public void setHudStarredProjectIds(List<String> projectIds) {
        if (gui.hudStarredProjectIds == null) {
            gui.hudStarredProjectIds = new ArrayList<>();
        } else {
            gui.hudStarredProjectIds.clear();
        }
        if (projectIds != null) {
            for (String projectId : projectIds) {
                if (projectId == null) {
                    continue;
                }
                String normalizedProjectId = projectId.trim();
                if (normalizedProjectId.isEmpty() || gui.hudStarredProjectIds.contains(normalizedProjectId)) {
                    continue;
                }
                gui.hudStarredProjectIds.add(normalizedProjectId);
            }
        }
        save();
    }

    public String getLastActiveProjectId() {
        return getLastActiveProjectId(DataPathProvider.getStorageNamespace());
    }

    public String getLastActiveProjectId(String namespace) {
        if (gui.lastActiveProjectIdsByNamespace == null) {
            return null;
        }
        String key = normalizeNamespaceKey(namespace);
        return gui.lastActiveProjectIdsByNamespace.get(key);
    }

    public void setLastActiveProjectId(String projectId) {
        setLastActiveProjectId(DataPathProvider.getStorageNamespace(), projectId);
    }

    public void setLastActiveProjectId(String namespace, String projectId) {
        if (gui.lastActiveProjectIdsByNamespace == null) {
            gui.lastActiveProjectIdsByNamespace = new HashMap<>();
        }
        String key = normalizeNamespaceKey(namespace);
        if (projectId == null || projectId.trim().isEmpty()) {
            gui.lastActiveProjectIdsByNamespace.remove(key);
        } else {
            gui.lastActiveProjectIdsByNamespace.put(key, projectId.trim());
        }
        save();
    }

    private String normalizeNamespaceKey(String namespace) {
        if (namespace == null) {
            return DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        String trimmed = namespace.trim();
        if (trimmed.isEmpty()) {
            return DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        return trimmed.toLowerCase();
    }

    public int getProjectSidebarWidth() { return gui.projectSidebarWidth; }
    public void setProjectSidebarWidth(int width) {
        gui.projectSidebarWidth = clamp(width, SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX);
        save();
    }

    /**
     * 解析默认 HUD 的右上角落点。
     *
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @param hudWidth HUD 宽度
     * @param hudHeight HUD 高度
     * @return 默认 HUD 落点
     */
    private HudPlacement resolveDefaultHudPlacement(int screenWidth, int screenHeight, int hudWidth, int hudHeight) {
        int maxX = Math.max(0, screenWidth - Math.max(0, hudWidth));
        int maxY = Math.max(0, screenHeight - Math.max(0, hudHeight));
        int resolvedX = Math.max(0, maxX - HUD_DEFAULT_MARGIN);
        int resolvedY = Math.min(HUD_DEFAULT_MARGIN, maxY);
        return new HudPlacement(resolvedX, resolvedY);
    }

    /**
     * 按绝对坐标刷新 HUD 锚点和边距状态。
     *
     * @param absoluteX HUD 绝对 X 坐标
     * @param absoluteY HUD 绝对 Y 坐标
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @param hudWidth HUD 宽度
     * @param hudHeight HUD 高度
     */
    private void updateHudCustomAnchorState(int absoluteX, int absoluteY, int screenWidth, int screenHeight, int hudWidth, int hudHeight) {
        int clampedX = clampHudCoordinate(absoluteX, screenWidth, hudWidth);
        int clampedY = clampHudCoordinate(absoluteY, screenHeight, hudHeight);
        gui.hudCustomX = clampedX;
        gui.hudCustomY = clampedY;

        int leftMargin = clampedX;
        int rightMargin = Math.max(0, screenWidth - Math.max(0, hudWidth) - clampedX);
        if (leftMargin <= rightMargin) {
            gui.hudCustomHorizontalAnchor = HudHorizontalAnchor.LEFT;
            gui.hudCustomHorizontalMargin = leftMargin;
        } else {
            gui.hudCustomHorizontalAnchor = HudHorizontalAnchor.RIGHT;
            gui.hudCustomHorizontalMargin = rightMargin;
        }

        int topMargin = clampedY;
        int bottomMargin = Math.max(0, screenHeight - Math.max(0, hudHeight) - clampedY);
        if (topMargin <= bottomMargin) {
            gui.hudCustomVerticalAnchor = HudVerticalAnchor.TOP;
            gui.hudCustomVerticalMargin = topMargin;
        } else {
            gui.hudCustomVerticalAnchor = HudVerticalAnchor.BOTTOM;
            gui.hudCustomVerticalMargin = bottomMargin;
        }
    }

    /**
     * 根据绝对像素值推导比例坐标。
     *
     * @param absoluteCoordinate 当前绝对坐标
     * @param screenSize 当前屏幕尺寸
     * @param hudSize HUD 尺寸
     * @return 0.0 ~ 1.0 的比例值
     */
    private static double deriveHudPositionRatio(int absoluteCoordinate, int screenSize, int hudSize) {
        int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
        if (maxCoordinate <= 0) {
            return 0.0;
        }
        int clampedCoordinate = clampHudCoordinate(absoluteCoordinate, screenSize, hudSize);
        return clampRatio((double) clampedCoordinate / (double) maxCoordinate);
    }

    /**
     * 根据比例坐标解析绝对像素坐标。
     *
     * @param ratio 0.0 ~ 1.0 的比例值
     * @param screenSize 当前屏幕尺寸
     * @param hudSize HUD 尺寸
     * @return HUD 绝对坐标
     */
    private static int resolveHudCoordinate(double ratio, int screenSize, int hudSize) {
        int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
        if (maxCoordinate <= 0) {
            return 0;
        }
        return clampHudCoordinate((int) Math.round(clampRatio(ratio) * maxCoordinate), screenSize, hudSize);
    }

    /**
     * 根据水平锚点和边距解析 HUD X 坐标。
     *
     * @param screenWidth 当前屏幕宽度
     * @param hudWidth HUD 宽度
     * @param anchor 水平锚点
     * @param margin 水平边距
     * @return HUD X 坐标
     */
    private static int resolveHudXByAnchor(int screenWidth, int hudWidth, HudHorizontalAnchor anchor, int margin) {
        int resolved = anchor == HudHorizontalAnchor.LEFT
                ? Math.max(0, margin)
                : screenWidth - Math.max(0, hudWidth) - Math.max(0, margin);
        return clampHudCoordinate(resolved, screenWidth, hudWidth);
    }

    /**
     * 根据垂直锚点和边距解析 HUD Y 坐标。
     *
     * @param screenHeight 当前屏幕高度
     * @param hudHeight HUD 高度
     * @param anchor 垂直锚点
     * @param margin 垂直边距
     * @return HUD Y 坐标
     */
    private static int resolveHudYByAnchor(int screenHeight, int hudHeight, HudVerticalAnchor anchor, int margin) {
        int resolved = anchor == HudVerticalAnchor.TOP
                ? Math.max(0, margin)
                : screenHeight - Math.max(0, hudHeight) - Math.max(0, margin);
        return clampHudCoordinate(resolved, screenHeight, hudHeight);
    }

    /**
     * 将 HUD 坐标裁剪到当前可视区域内。
     *
     * @param coordinate 原始坐标
     * @param screenSize 当前屏幕尺寸
     * @param hudSize HUD 尺寸
     * @return 裁剪后的坐标
     */
    private static int clampHudCoordinate(int coordinate, int screenSize, int hudSize) {
        int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
        if (coordinate < 0) {
            return 0;
        }
        if (coordinate > maxCoordinate) {
            return maxCoordinate;
        }
        return coordinate;
    }

    /**
     * 裁剪 HUD 比例坐标。
     *
     * @param ratio 原始比例值
     * @return 0.0 ~ 1.0 的合法比例
     */
    private static double clampRatio(double ratio) {
        if (ratio < 0.0) {
            return 0.0;
        }
        if (ratio > 1.0) {
            return 1.0;
        }
        return ratio;
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


