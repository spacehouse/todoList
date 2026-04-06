package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.client.TodoHudRenderer;
import com.todolist.config.ModConfig;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Todo List GUI Screen
 *
 * Features:
 * - Display task list
 * - Add/Edit/Delete tasks
 * - Mark tasks as complete
 * - Filter by priority/status
 * - Project management (Sidebar)
 */
public class TodoScreen extends Screen implements ProjectManager.ProjectChangeListener {
    private static final Component TITLE = Component.translatable("gui.todolist.title");

    private enum ViewMode {
        PERSONAL,
        TEAM_UNASSIGNED,
        TEAM_ALL,
        TEAM_ASSIGNED
    }

    /**
     * 濞戞捁宕甸弲顐︽閵忋垺鐣辩紒灞炬そ濡法绱掗弶鎴濐唺闁挎稑鑻亸顖炲礆閸℃洟鍤嬪ù婊嗘閳规牠姊荤紙鐘电憿闁搞儯鍨藉Σ锔剧矚濞差亝锛熼柕?     */
    private enum SpaceMode {
        PERSONAL,
        TEAM
    }

    /**
     * 濞戞捁宕甸弲顐︽閵忋垺鐣卞ù鐘侯嚙婵喓鎲撮崱妤佺缂備焦娼欑€规娊鏁嶅畝鈧划鐑樼▔閳ь剚绋夐鍐╃溄濞戞挸楠稿ú鐔兼⒓閻旇　鏁勯梻鍌氼嚟濞堟垿宕ｉ婵愭綄閻熸瑥妫楀ù妯兼嫚椤撴繄鐤呴柕?     */
    private enum TaskViewOption {
        MY,
        UNASSIGNED,
        ALL
    }

    /**
     * 濞戞捁宕甸弲顐︽閵忋垺鐣遍柛婵嗙Т缁ㄦ彃顕ｈ箛鏂烩偓鍌涙媴瀹ュ繒绀夐柟绋款槺閻涖儵宕ｉ敐鍜佸晬濡ゅ倹顭囬幃锝夊触閸繂鏋€閻庤鑹剧粩椋庝沪閳ь剟姊藉鍥崜缂佹稒鐗滈弳鎰板Υ?     */
    private enum ResponsiveTier {
        LARGE,
        MEDIUM,
        COMPACT,
        MINIMAL
    }

    /**
     * 缂佺姭鍋撻柛妤佹礈閻撯晞銇愰姀鐘殿伌閻忕偐鍋撻悗鐢殿攰閽栧嫰鏁嶅畝鍐惧敹鐟滅増娲橀悡鍥ㄧ▔椤忓嫬闅橀柛鈺冨枔濞堟垶娼忛崷顓熸珪濞ｅ洠鍓濇导鍛村Υ?     */
    private static final class LayoutRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        /**
         * 闁告帗绋戠紓鎾寸▔閳ь剚绋夐鍡欏彁鐟滆埇鍨肩粩鐔兼偩鐏炵瓔鍤犻悹鐏烘壋鍋?         *
         * @param x 鐎归潻缂氱粭鍌滄喆閹烘拋顓㈠锤閹邦厾鍨?
         * @param y 鐎归潻缂氱粭鍌滄喆閹烘梹妫婚柛褎鍔栭悥?
         * @param width 闁告牕鎼悡娆戔偓纭呮鐎?
         * @param height 闁告牕鎼悡娆愵殗濡搫顔?
         */
        private LayoutRect(int x, int y, int width, int height) {
            this.x = Math.max(0, x);
            this.y = Math.max(0, y);
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        /**
         * 闁告帇鍊栭弻鍥锤閹邦厾鍨奸柡鍕靛灠閹焦鎷呭鍕壘鐟滅増鎸告晶鐘绘儗閳轰浇鍩岄柛鎰嚇閸庢挳濡?         *
         * @param mouseX 濮捬呭У閻栵絽螣椤忓嫭缍忛柡?         * @param mouseY 濮捬呭У閻栵絿鐥棃娑欑稄闁?         * @return true 閻炴稏鍔庨妵姘跺川閹存帟鍘憸鐗堟尭婢х娀宕犻崫鍕幍
         */
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        /**
         * 濞寸姰鍎茬粊瀵告嫚閺囩偛鍑犲┑鍌滄櫕濞堟垿寮幍顔剧煁鐟滆埇鍨圭槐鈩冩交閺傛寧绀€闁活厸鏅涢懜鐗堟綇閸︻厽娅曢柕?         *
         * @return 濞撴碍绻冮濂稿礌閸涱厽鍎?x闁靛棔绨滈柕鍡曠皻idth闁靛棔寮揺ight 闁汇劌瀚弳鐔虹磼?         */
        private int[] toArray() {
            return new int[] {x, y, width, height};
        }
    }

    /**
     * 濞戞捁宕甸弲顐︽閵忕姷顏撮悘鐐╁亾闊浂鍋嗛崣搴ㄦ晬瀹€鍕偁濞戞搩鍘虹换姘扁偓娑櫭幖閿嬫償閺傝法纭€闁哄偆鍘鹃崑锝夊椽鐏炶偐鐟忛柡宥呯箰鐏忣垶宕洪悢鑽ょ彾闁伙絽琚埀?     */
    private static final class MainLayoutMetrics {
        private final ResponsiveTier responsiveTier;
        private final LayoutRect sidebarBounds;
        private final LayoutRect contentBounds;
        private final LayoutRect detailBounds;
        private final boolean sidebarOverlay;
        private final boolean detailOverlay;
        private final boolean sidebarVisible;
        private final boolean detailVisible;
        private final int padding;
        private final int gap;

        /**
         * 闁告帗绋戠紓鎾寸▔閳ь剙鈻庨垾鎻掔槣闁伙絽鐭傚鎵暜閸愩劎婀伴悹渚婄磿閻ｈ崵绱掗幘瀵镐函闁?         *
         * @param responsiveTier 鐟滅増鎸告晶鐘诲传瀹ュ懐瀹夌€殿喖绻戦妴鍌涙媴?         * @param sidebarBounds 濡炪倕婀卞ú鐗堢瑹瑜庨悥顔芥綇閸︻厽娅?
         * @param contentBounds 濞戞捁顕ч崬瀵糕偓鍦嚀鐏忣垱娼忛崷顓熸珪
         * @param detailBounds 閻犲浄闄勯崕蹇涘礌妤﹁法鐝堕柣?         * @param sidebarOverlay 濡炪倕婀卞ú鐗堢瑹瑜庨悥顕€寮伴姘剨閻熸洖妫涘ú濠囧及閸撗佷粵
         * @param detailOverlay 閻犲浄闄勯崕蹇涘礌閻戞ɑ笑闁告熬绠掗々顐︽儎閺嶃劍鈻旂紒鈧?         * @param sidebarVisible 濡炪倕婀卞ú鐗堢瑹瑜庨悥顔裤亹閹惧啿顤呴柡鍕靛灠閹線宕ｉ婵愭綄
         * @param detailVisible 閻犲浄闄勯崕蹇涘礌閸濆嫮绉奸柛鎾崇У濡叉悂宕ラ敃鈧ぐ鑼喆?         * @param padding 濠㈣埖鐗曢惇鐗堟綇绾懐鐛?
         * @param gap 闂傚牄鍨哄姗€姊荤壕瀣崺
         */
        private MainLayoutMetrics(ResponsiveTier responsiveTier,
                                  LayoutRect sidebarBounds,
                                  LayoutRect contentBounds,
                                  LayoutRect detailBounds,
                                  boolean sidebarOverlay,
                                  boolean detailOverlay,
                                  boolean sidebarVisible,
                                  boolean detailVisible,
                                  int padding,
                                  int gap) {
            this.responsiveTier = responsiveTier;
            this.sidebarBounds = sidebarBounds;
            this.contentBounds = contentBounds;
            this.detailBounds = detailBounds;
            this.sidebarOverlay = sidebarOverlay;
            this.detailOverlay = detailOverlay;
            this.sidebarVisible = sidebarVisible;
            this.detailVisible = detailVisible;
            this.padding = padding;
            this.gap = gap;
        }
    }

    /**
     * 閻犲浄闄勯崕蹇涘箮閽樺婧勯柤钘夘槺椤牏鈧數顢婇挅鍕晬瀹€鍕偁濞戞搩鍘惧ǎ顕€骞庨妶鍛Ъ闁告挸绉归埀顒€顦懙鎴炵鐠囨彃顫ら柣銊ュ缁鳖亝娼忛幋鐐╁亾娴ｅ憡瀚查柡鍕⒔閵囨岸骞€娴ｇ鍋?     */
    private static final class TaskDetailDraft {
        private final String taskId;
        private String title;
        private String description;
        private String tags;
        private boolean titleEditing;

        /**
         * 闁告帗绋戠紓鎾寸▔閳ь剚绋夐鍐憿闂侇偄顦懙鎴炵鐠囨彃顫ょ紓浣瑰灥閻ｉ箖鎯冮崟顕呭殜闁诡垰鎳撳畷蹇曠矙鐟併倐鍋?         *
         * @param taskId 濞寸姾顕ф慨鐔煎冀閸ヮ亞妲?
         * @param title 闁哄秴娲。浠嬪棘閸ャ劍鎷?
         * @param description 闁硅绻楅崼顏堝棘閸ャ劍鎷?
         * @param tags 闁哄秴娲ㄩ鐑藉棘閸ャ劍鎷?
         */
        private TaskDetailDraft(String taskId, String title, String description, String tags) {
            this.taskId = taskId;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.tags = tags == null ? "" : tags;
            this.titleEditing = false;
        }
    }

    private final Screen parent;
    private ProjectManager projectManager;
    private ProjectListWidget projectListWidget;
    private Project currentProject;
    private TaskManager taskManager;
    private TaskManager personalTaskManager;
    private TaskManager teamTaskManager;
    private TaskListWidget taskListWidget;
    private final List<Notification> notifications = new ArrayList<>();

    private ViewMode viewMode = ViewMode.PERSONAL;
    private SpaceMode currentSpaceMode = SpaceMode.PERSONAL;
    private TaskViewOption currentTaskViewOption = TaskViewOption.MY;
    private boolean completedExpanded;

    // Input fields
    private EditBox searchField;
    private EditBox quickAddField;
    private EditBox titleField;
    private MultiLineEditBox descField;
    private EditBox tagField;

    // Buttons
    private Button detailCloseButton;
    private Button claimButton;
    private Button abandonButton;
    private Button assignOthersButton;
    private Button saveButton;
    private Button cancelButton;
    private Button sidebarToggleButton;

    // Selected priority for new/edited tasks
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter buttons
    private Button filterStatusButton;
    private Button filterPriorityButton;
    private Button viewToggleButton;
    private Button configButton;
    
    // Project Search & Toggle
    private EditBox projectSearchField;
    private Button addProjectBtn;
    private Button personalSpaceButton;
    private Button teamSpaceButton;
    private Button myViewButton;
    private Button unassignedViewButton;
    private Button allViewButton;
    private Button editProjectBtn;
    private Button deleteProjectBtn;
    private Button applyJoinProjectBtn;
    private Project.Scope projectScopeFilter = Project.Scope.PERSONAL;
    private String projectSearchQuery = "";
    private String preferredPersonalProjectId;
    private String preferredTeamProjectId;
    private boolean teamProjectsEnabled = true;
    private int savedProjectListScrollOffset;
    
    private int currentPriorityFilter = 0; // 0=All, 1=High, 2=Medium, 3=Low
    
    private Task selectedTask;
    private Task pendingClickSelectionTask;
    private boolean taskRowDragInProgress;
    private List<String> taskRowDragOrderSnapshot = List.of();
    private List<Task> filteredTasks = new ArrayList<>();
    private List<Task> baseFilteredTasks = new ArrayList<>();
    private String currentFilter = "active";
    private String searchQuery = "";
    private boolean hasUnsavedChanges = false;
    private static boolean personalHasUnsavedChanges = false;
    private static boolean teamHasUnsavedChanges = false;
    private static LastGuiState lastGuiState;
    private String openedStorageNamespace = DataPathProvider.getStorageNamespace();
    private Task contextMenuTask;
    private int contextMenuX;
    private int contextMenuY;
    private int contextMenuWidth;
    private int contextMenuItemHeight = 18;
    private List<ContextMenuItem> contextMenuItems = new ArrayList<>();
    private ResponsiveTier responsiveTier = ResponsiveTier.LARGE;
    private boolean sidebarOverlayVisible;
    private boolean detailOverlayVisible;
    private MainLayoutMetrics layoutMetrics;
    private TaskDetailDraft detailDraft;
    private boolean syncingDetailWidgets;

    private static class LastGuiState {
        Project.Scope projectScopeFilter;
        String currentProjectId;
        ViewMode viewMode;
        SpaceMode spaceMode;
        TaskViewOption taskViewOption;
        boolean completedExpanded;
        int currentPriorityFilter;
        String currentFilter;
        String searchQuery;
        String projectSearchQuery;
        String lastPersonalProjectId;
        String lastTeamProjectId;
    }

    private static class ContextMenuItem {
        final Component text;
        final boolean enabled;
        final Runnable action;

        ContextMenuItem(Component text, boolean enabled, Runnable action) {
            this.text = text;
            this.enabled = enabled;
            this.action = action;
        }
    }


    /**
     * 闁告帗绋戠紓鎾寸▔閼姐倖娅曢梻鍫緛缁辨繈鐛捄鐑樿含闁稿繑濞婂Λ鎾籍閹壆绠查柛銉у仜閸╁矂鎮ラ崜浣规珪闂傚牜娼块埀?
     */
    public TodoScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    /**
     * 闂佹彃绉堕悿鍡樼▔閼姐倖娅曢梻鍫涘灮濞堟垿妫冨▎鎰ㄥ亾娴ｇ晫绠ラ悶娑樻湰閳ь兛绶ょ槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕绶氬▓褏绮嬮懡銈嗘殢濞撴艾顑冮埀?
     */
    static void resetGuiStateForTest() {
        personalHasUnsavedChanges = false;
        teamHasUnsavedChanges = false;
        lastGuiState = null;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭銈呮贡濞蹭即鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓寮鈾€鏋呭銈呮贡濞蹭即宕氶崶銊ュ簥濞戞挸瀛╂禒顔藉緞瀹ュ鍋撻弰蹇曞竼闁?
     *
     * @return 鐟滅増鎸告晶鐘炽亜閸︻厽绐楅柨娑欑◥缁楀鈧稒锚濠€顏堝籍閹壆绠查柛?null
     */
    Project getCurrentProjectForTest() {
        return currentProject;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴梺顐㈩槷閼垫垶绂掔拠鎻掝潳闁挎稑濂旂欢鐢稿触鐏炶棄鐦舵繛鏉戭儓閻︻垱绂掗敐鍥╁灣闁哄偆鍙€閳诲牓鏌呮径瀣仴闂侇偅妲掔欢顐﹀Υ?
     *
     * @return 鐟滅増鎸告晶鐘绘焻婢跺鍘ù鐘侯嚙婵喖鏁嶅☉妤冪憹閻庢稒锚濠€顏堝籍閹壆绠查柛?null
     */
    Task getSelectedTaskForTest() {
        return selectedTask;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴悷娆忔濞存ê螣閳ュ磭纭€闁告艾绉惰ⅷ闁挎稑濂旂欢鐢稿触鐏炶棄鐦舵繛鏉戭儓閻︻垱绂掗敐鍥╁灣闁哄偆鍙€閳诲牏鎲撮崱妤佺闁诡厹鍨归ˇ鏌ユ焻閺勫繒甯嗛柕?
     *
     * @return 鐟滅増鎸告晶鐘垫喆閸℃绂堟俊顖椻偓宕囩闁告艾绉惰ⅷ
     */
    String getViewModeNameForTest() {
        return viewMode.name();
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呯紒灞炬そ濡灝螣閳ュ磭纭€闁告艾绉惰ⅷ闁挎稑濂旂欢鐢稿触鐏炶棄鐦舵繛鏉戭儓閻︻垱绂掗敐鍥╁灣闁哄偆鍙€閳诲牊绋夐鍐╃溄/闁搞儯鍨藉Σ锔剧矚濞差亝锛熼柛鎺戞处瀹曡尙鎷犻婵堢枀闁?     *
     * @return 鐟滅増鎸告晶鐘电矚濞差亝锛熸俊顖椻偓宕囩闁告艾绉惰ⅷ
     */
    String getCurrentSpaceModeNameForTest() {
        return currentSpaceMode.name();
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴柛娆樺灥椤棙绂掔拠鎻掝潳閻熸瑥妫楀ù姗€鏌呮径鎰┾偓宥堢疀椤愩倕寮鹃柨娑樺缁剁敻宕ョ仦钘夌樁婵炴潙顑堥惁顖涚閿濆洨鍨抽柡鍌ゅ弨閳诲牏绮氬ú顏咃紵濞戞挸姘﹂～瀣炊閻愵剚衼閻忓繐瀚崣褏鍖栧Ч鍥ｅ亾?     *
     * @return 鐟滅増鎸告晶鐘诲矗椤栨繍娼屽ù鐘侯嚙婵喓鎲撮崱妤佺闂侇偄顦甸妴宥夊触瀹ュ泦鐐哄礆濡ゅ嫨鈧?
     */
    List<String> getVisibleTaskViewOptionNamesForTest() {
        List<String> names = new ArrayList<>();
        for (TaskViewOption option : buildVisibleViewOptions(currentSpaceMode)) {
            names.add(option.name());
        }
        return List.copyOf(names);
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喓鎲撮崱妤佺闂侇偄顦甸妴宥夊触瀹ュ泦鐐烘晬鐏炶偐杩旈柛姘嫰鐎垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓寮鈾€鏋呴悷娆忔濞存﹢宕氶崶銊ュ簥閻犲浂鍘虹粻鐔煎Υ?     *
     * @return 鐟滅増鎸告晶鐘崇鐠囨彃顫ら悷娆忔濞存﹢鏌呮径鎰┾偓宥夊触瀹ュ泦?
     */
    String getCurrentTaskViewOptionNameForTest() {
        return currentTaskViewOption.name();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ь啅閹绘帞鏆氶柟瀛樺姇閸ㄥ海绱掗崟顒佇﹂柛姘剧畱閻秴顕ｉ埀顒勬晬鐏炶偐杩旈柛姘嫰鐎垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓寮鈾€鏋呴柟鑸得ぐ鏃堟偐閼哥鍋撴担绋跨€奸柟璇℃娇閳?     *
     * @return true 閻炴稏鍔庨妵姘啅閹绘帞鏆氶柟瀛樺姇閸ㄥ海绱掗崟顐㈠殥閻忕偞娲栫槐?
     */
    boolean isCompletedSectionExpandedForTest() {
        return completedExpanded;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭☉鎾瑰吹閺咁偊妫冮姀銏＄暠闁告繂绉寸花鎻掝嚕韫囨柣鈧倹鎷呭鍛€崇紒澶婂簻缁辨繃绗熷☉娆戙偞閻犲洦娲橀弻鍥╂嚊閳ь剟寮鐘蹭化闁告稒鍨濋懙鎴犵磼閹惧浜柕?     *
     * @return 鐟滅増鎸告晶鐘诲传瀹ュ懐瀹夌€殿喖绻戦妴鍌涙媴瀹ュ懏鍊崇紒?     */
    String getResponsiveTierNameForTest() {
        return responsiveTier.name();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楀〒姘€鍕焿鐟滅増鎸告晶鐘诲及椤栨碍鍎婂ù鐘劥椤╊偊鎯勯弽褏纭€閻㈩垰鍟惇顒勫川閸垹绠涢柕?     *
     * @return true 閻炴稏鍔庨妵姘亜閸︻厽绐楀〒姘€鍕焿濠㈣泛瀚花顒傛啺閸℃瑦纾版俊顖椻偓宕囩
     */
    boolean isProjectSidebarOverlayForTest() {
        return layoutMetrics != null && layoutMetrics.sidebarOverlay;
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柛鏍ф惈缂嶅宕滃鍡樞﹂柛姘剧細娴滄帞鎲伴崱娆愮０鐎殿喖绻愮粩椋庝沪閳ь剟宕ㄩ崼銏犵疀闁?     *
     * @return true 閻炴稏鍔庨妵姘辨嫚閿旇棄鍓伴柛鏍ф惈椤︹晜绂嶆惔銈庢船闁烩晜鐗楄啯鐎?     */
    boolean isDetailPanelOverlayForTest() {
        return layoutMetrics != null && layoutMetrics.detailOverlay;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楀〒姘€鍕焿鐟滅増鎸告晶鐘诲及椤栨碍鍎婇柛娆樺灥椤棝濡?     *
     * @return true 閻炴稏鍔庨妵姘亜閸︻厽绐楀〒姘€鍕焿闁告瑯鍨甸～?
     */
    boolean isProjectSidebarVisibleForTest() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柛鏍ф惈缂嶅宕滃鍡樞﹂柛姘剧畱瑜拌尙鎲存担纰樺亾?     *
     * @return true 閻炴稏鍔庨妵姘辨嫚閿旇棄鍓伴柛鏍ф惈瑜拌尙鎲?     */
    boolean isDetailPanelVisibleForTest() {
        return layoutMetrics != null && layoutMetrics.detailVisible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楀〒姘€鍕焿闁告帒娲﹀畷鏌ュ箰婢舵劖灏︾憸鐗堟尭婢х娀寮伴姘剨闁告瑯鍨甸～鍡涘Υ?     *
     * @return true 閻炴稏鍔庨妵姘亜閸︻厽绐楀〒姘€鍕焿闁告帒娲﹀畷鏌ュ箰婢舵劖灏﹂柛娆樺灥椤?
     */
    boolean isSidebarToggleButtonVisibleForTest() {
        return sidebarToggleButton != null && sidebarToggleButton.visible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楀〒姘€鍕焿闁告牕鎼悡娆愭綇閸︻厽娅曢柨娑樺缁堕潧霉鐎ｎ厾妲稿Δ鐘茬焷閻﹀宕鍛畨鐎殿喖绻愮粩椋庝沪閳ь剟濡?     *
     * @return 濞撴皜鍕焿閺夊牆婀遍弲顐﹀极閹殿喚鐭?
     */
    int[] getProjectSidebarBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.sidebarBounds.toArray();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ▔鐠囨彃鏁堕悗鍦嚀鐏忣垱娼忛崷顓熸珪闁挎稑濂旂欢闈浢圭€ｎ厾妲稿Δ鐘茬焷閻﹀宕鍛畨鐎殿喖绻愮粩椋庝沪閳ь剟濡?     *
     * @return 濞戞捁顕ч崬瀵糕偓鍦嚀鐏忣垱娼忛崷顓熸珪闁轰焦澹嗙划?
     */
    int[] getContentAreaBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.contentBounds.toArray();
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柛鏍細缁旂喖鎮惧畝瀣濞撴碍绋掔粊瀵告嫚閺囥垻宕ｉ悹鍥︾閹奸攱鎯旈弬璺ㄧ閻㈩垰鍟惇顒勫Υ?     *
     * @return 閻犲浄闄勯崕蹇涘礌妤﹁法鐝堕柣锝呮湰閺嗙喓绱?     */
    int[] getDetailPanelBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.detailBounds.toArray();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楅柛鎺擃殙閵嗗啴宕犻崫鍕幍閺夊牆婀遍弲顐︽晬鐏炶偐杩旀繛鏉戭儓閻︻垱顨ュ畝鍐閹煎瓨娲熼崕鎾箼瀹ュ嫮绋婇柛鏍ф惈濞存劗鈧鑹剧粩椋庝沪閳ь剟濡?     *
     * @return 濡炪倕婀卞ú浼村礆濡ゅ嫨鈧啴宕犻崫鍕幍閺夊牆婀遍弲顐﹀极閹殿喚鐭?
     */
    int[] getProjectListBoundsForTest() {
        return projectListWidget == null ? new int[] {0, 0, 0, 0} : projectListWidget.getBoundsForTest();
    }

    /**
     * 閺夆晜鏌ㄥú鏍棘閺夋鏉诲銈呮贡濞蹭即骞愭径鎰唉閺夊牆婀遍弲顐﹀Υ?     *
     * @return 闁哄倹婢橀·鍐┿亜閸︻厽绐楅柟绋款樀閹歌櫕娼忛崷顓熸珪闁轰焦澹嗙划?
     */
    int[] getAddProjectButtonBoundsForTest() {
        return toWidgetBounds(addProjectBtn);
    }

    /**
     * 閺夆晜鏌ㄥú鏍磽閺嶎剛甯嗗銈呮贡濞蹭即骞愭径鎰唉閺夊牆婀遍弲顐﹀Υ?     *
     * @return 缂傚倹鐗炵欢顐ｃ亜閸︻厽绐楅柟绋款樀閹歌櫕娼忛崷顓熸珪闁轰焦澹嗙划?
     */
    int[] getEditProjectButtonBoundsForTest() {
        return toWidgetBounds(editProjectBtn);
    }

    /**
     * 閺夆晜鏌ㄥú鏍礆閻樼粯鐝熷銈呮贡濞蹭即骞愭径鎰唉閺夊牆婀遍弲顐﹀Υ?     *
     * @return 闁告帞濞€濞呭孩銇勯崷顓熺獥闁圭顦甸幐铏綇閸︻厽娅曢柡浣瑰缁?
     */
    int[] getDeleteProjectButtonBoundsForTest() {
        return toWidgetBounds(deleteProjectBtn);
    }

    /**
     * 閺夆晜鏌ㄥú鏍灳濠婂懏鏆ら悹鍥у槻婵偤宕楅妷鈶╁亾濠靛洤鐦婚梺绛嬪枛缂嶅宕滃鍡樞﹂柛姘剧畱瑜拌尙鎲存担纰樺亾?     *
     * @return true 閻炴稏鍔庨妵姘舵偨鐎圭媭鍤為柛鏃傚Т閸欏棝骞愭径鎰唉闁告瑯鍨甸～?
     */
    boolean isApplyJoinProjectButtonVisibleForTest() {
        return applyJoinProjectBtn != null && applyJoinProjectBtn.visible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍礆閻樼粯鐝熷銈呮贡濞蹭即骞愭径鎰唉鐟滅増鎸告晶鐘诲及椤栨碍鍎婇柛娆樺灥椤棝濡?     *
     * @return true 閻炴稏鍔庨妵姘跺礆閻樼粯鐝熷銈呮贡濞蹭即骞愭径鎰唉闁告瑯鍨甸～?
     */
    boolean isDeleteProjectButtonVisibleForTest() {
        return deleteProjectBtn != null && deleteProjectBtn.visible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍磽閺嶎剛甯嗗銈呮贡濞蹭即骞愭径鎰唉鐟滅増鎸告晶鐘诲棘閸ヮ煈鏀抽柨娑樺缁堕潧霉鐎ｎ厾妲稿Δ鐘茬焷閻﹀鍨惧鍛そ閺?闁哄被鍎冲﹢鍛村灳濠靛顎栫紒鐙欏棭鍤斿☉鏂款槶閳?     *
     * @return 缂傚倹鐗炵欢顐ｃ亜閸︻厽绐楅柟绋款樀閹告娊寮崶顭戞敵
     */
    String getEditProjectButtonTextForTest() {
        return editProjectBtn == null ? "" : editProjectBtn.getMessage().getString();
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴柣妯垮煐閳ь兛鑳堕悺顐︽焻婢跺ň鍋撶涵椋庣濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担瑙勭劷閻熷皝鍋撻弶鈺佹处閹躲倝鏌呴弰蹇曞竼闁?
     *
     * @return 鐟滅増鎸告晶鐘绘偐閼哥鍋撴担铏规懀闂侇偄顦埀?
     */
    String getCurrentFilterForTest() {
        return currentFilter;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鍏济崢娑氱棯瑜忛悺顐︽焻婢跺ň鍋撶涵椋庣濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担瑙勭劷閻熷皝鍋撻弶鈺佹处閹躲倝鏌呴弰蹇曞竼闁?
     *
     * @return 鐟滅増鎸告晶鐘冲濡搫甯ョ紒鐙欏懐鎽ｉ梺顐㈩槸閳?
     */
    int getCurrentPriorityFilterForTest() {
        return currentPriorityFilter;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴柟鍏肩矌閸屻劑宕楅幎鑺ユ殯閻庢稒顨愮槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕鐒﹂弻鍥╂嚊閳ь剟骞栧鍛亶闁诡厹鍨归ˇ鏌ユ焻閺勫繒甯嗛柕?
     *
     * @return 鐟滅増鎸告晶鐘诲箹濠婂懎鍋嶉柛蹇斿▕閺侇厾鈧?
     */
    String getSearchQueryForTest() {
        return searchQuery;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴梺顐ｆ皑閻擄繝寮导鏉戞闁挎稑濂旂欢鐢稿触鐏炶棄鐦舵繛鏉戭儓閻︻垱绂掗敐鍥╁灣闁哄偆鍙€閳诲牓骞撻幇顔轰粵閻炴稑濂旂拹鐔煎Υ?
     *
     * @return 鐟滅増鎸告晶鐘绘焻濮樿京鍙€闁轰椒鍗抽崳?
     */
    int getNotificationCountForTest() {
        return notifications.size();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柡宥呮喘椤ｈ姤娼忛幘鍐插汲婵℃妫寸槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕绀侀崯鎾诲礂閵夈倖宕查柛鏂哄墲閻栵絾锛愬Ο绯曞亾?
     *
     * @return 濞寸姾顕ф慨鐔煎冀閸ヮ剦鏆弶鍫熸尭閸欏棗顩?
     */
    EditBox getTitleFieldForTest() {
        return titleField;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄦ償閺囥垹鍔ラ煫鍥跺亰閳ь剛鍠愰弻濠冩櫠閻愬墎缈婚柛蹇嬪劜椤㈠鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粍銇欓崡鐐残楅柡鍌涙緲椤ゅ啯绂掔拠鎻掝潳婵炵繝鑳堕埢濂稿Υ?     *
     * @return 閹煎瓨娲熼崕纾嬬疀椤愶腹鍋撻悢鍛婄厐濠⒀呭仩缁额參宕楅妷锔绘敱
     */
    EditBox getQuickAddFieldForTest() {
        return quickAddField;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柟璇茬箺閸亝娼忛幘鍐插汲婵℃妫寸槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕绀侀崯鎾诲礂閵夈倖宕查柛鏂哄墲瀵寧娼婚懜顑藉亾?
     *
     * @return 濞寸姾顕ф慨鐔煎箵韫囨艾鐗氶弶鍫熸尭閸欏棗顩?
     */
    MultiLineEditBox getDescFieldForTest() {
        return descField;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柡宥呮川椤掗攱娼忛幘鍐插汲婵℃妫寸槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕绀侀崯鎾诲礂閵夛妇鍨肩紒娑欏劤閸炲鈧懓绠嶉埀?
     *
     * @return 濞寸姾顕ф慨鐔煎冀閸モ晩鍔弶鍫熸尭閸欏棗顩?
     */
    EditBox getTagFieldForTest() {
        return tagField;
    }

    /**
     * 閺夆晜鏌ㄥú鏍箹濠婂懎鍋嶉弶鍫熸尭閸欏棗顩奸崱顓犵濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆笟鈧埞宥夊礉閵婏附鍋濈紒渚垮灮閻☆偊鏌呮径鍫氬亾?
     *
     * @return 闁瑰吋绮庨崒銊︽綇閹惧啿寮虫俊?
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楅柟鍏肩矌閸屻劍娼忛幘鍐插汲婵℃妫寸槐婵囩瑹濞戞瑧銈撮悹鍥ㄦ礋閻涙瑧鎷犳担閿嬫珷闁哄秴绻楃换鍐煥閵堝嫮鐟㈤幖瀛樻礋閸庢挳骞愭径鎰唉閻㈩垰鍟惇顒勫Υ?     *
     * @return 濡炪倕婀卞ú浼村箹濠婂懎鍋嶉弶鍫熸尭閸欏棗顩?     */
    EditBox getProjectSearchFieldForTest() {
        return projectSearchField;
    }

    /**
     * 閺夆晜鏌ㄥú鏍偐閼哥鍋撴担铏规懀闂侇偄顦扮€垫粓鏌﹂鍡欑濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担绋跨€奸柟骞垮灩閻ｎ剟骞?闁哄牜浜滈悾顒勫箣閹邦喚鎽ｉ梺顐㈩槶閳?
     *
     * @return 闁绘鍩栭埀顑胯兌閻☆偊鏌呮径瀣樆闂?
     */
    Button getFilterStatusButtonForTest() {
        return filterStatusButton;
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄥ濡搫甯ョ紒鐙欏懐鎽ｉ梺顐㈩槹鐎垫粓鏌﹂鍡欑濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担绋跨€奸柟璇℃線缁鳖參宕楅崼銏ょ崜缂佹稒鐩埀顒€顦埀?
     *
     * @return 濞村吋锚閸樻稓鐥閻☆偊鏌呮径瀣樆闂?
     */
    Button getFilterPriorityButtonForTest() {
        return filterPriorityButton;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴柡鍕靛灠閹胶鈧稒锚濠€顏堝嫉椤忓啰绠介悗娑櫳戦弫濂稿礉椤帞绀夊〒姘☉閹捇宕犻崨顔俱偞閻犲洦娲戦崬顒勬儘娴ｈ鐒介悷灏佸亾濞ｅ洦绻傞悺銊︾▔鎼粹€冲綘闂傚偆鍙€椤曘垺绋婃径鍫氬亾?
     *
     * @return true 閻炴稏鍔庨妵姘炽亹閹惧啿顤呴悗娑櫭﹢顏堝嫉椤忓啰绠介悗娑櫳戦弫濂稿礉?
     */
    boolean hasUnsavedChangesForTest() {
        return hasUnsavedChanges;
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呯紒娑欑洴閳ь剙顦辩划銊╁几濠娾偓閹广垽宕濋垾铏渐闁绘挆宥囩濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担瑙勭劷閻熷皝鍋撻弶鈺佹处閹躲倖绋夋惔婵堢憪濞戞挸顑嗛弸鍐嚕濠婂啫绀嬮悶娑樺鐠愮喖濡?
     *
     * @return 鐟滅増鎸告晶鐘电驳濞戔懇鍋撴径宀€娉㈤柡瀣矆閹广垽宕濋垾铏渐闁?
     */
    List<Task> getFilteredTasksForTest() {
        return List.copyOf(filteredTasks);
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喓绮婚敍鍕€為柛锝冨妺閼垫垿鎯冮崟顐㈠伎闂侇喓鍔嬮幑銏ゅ礉閳ヨ櫕褰ラ柣鎾楀秶绀夊〒姘☉閹捇宕犻崨顔俱偞閻犲洦娲戦崬顒勬儘娴ｈ鐒介悷灏佸亾濞ｅ洦绻傞悺銊︾▔鎼粹€冲綘闂傚偆鍘奸幃妤呮儍閸曨剚娈堕柟璇″枤婵悂骞€娴ｇ鍋?
     *
     * @return 鐟滅増鎸告晶鐘崇鐠囨彃顫ょ紒鐙呯磿閹﹪宕抽妸銈堝幀闁汇劌瀚崣蹇涙焾閵娿倖宕查柛鏂衡偓铏渐闁?
     */
    List<Task> getCurrentManagerTasksForTest() {
        if (taskManager == null) {
            return List.of();
        }
        return taskManager.getAllTasks();
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭☉鎾筹梗缁楀懘寮崶顏勭秴闁告娲熼妴宥夊棘閸ャ劍鎷遍煫鍥跺亞閸欏酣鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓寮鈾€鏋呴柤鎸庣矊瀹曠喖宕橀崨顓у晣闁?
     *
     * @return 鐟滅増鎸告晶鐘崇▔婵犱胶鐟撻柡鍌氭穿瑜板秹宕￠弴顫偓宥夊棘閸ャ劍鎷遍煫鍥跺亞閸?
     */
    List<String> getContextMenuItemTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (ContextMenuItem item : contextMenuItems) {
            texts.add(item.text.getString());
        }
        return List.copyOf(texts);
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呴柡鍕靛灠閹線寮伴崜褋浠涘ù鐘侯嚙婵喐绋夋繝浣虹憮闁哄倸娲╄ぐ宥夊础閺囶亞绀夊〒姘☉閹捇宕犻崨顔俱偞閻犲洦娲戦崬顒勬儘娴ｈ鐒介悷灏佸亾闁兼寧绮屽畷鐔烘偘鐏炶壈绀嬮柕?
     *
     * @return true 閻炴稏鍔庨妵姘炽亹閹惧啿顤呴柡鍕⒔閵囨碍绂掔拠鎻掝潳濞戞挸锕ｇ粭鍛村棘閸ヮ亜缍呴柛?
     */
    boolean hasContextMenuForTest() {
        return hasContextMenu();
    }

    /**
     * 闁告帒娲﹀畷鑼躲亹閹惧啿顤呭銈呮贡濞蹭即鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓鎯勭€涙ê澶嶉悷鏇炴濞插﹥銇勯崷顓熺獥闁告帒娲﹀畷鍙夌▔閺勫繒鐔呯€垫澘瀚ㄩ埀?
     *
     * @param project 闁烩晩鍠楅悥锝嗐亜閸︻厽绐楅柨娑欑◥缁卞爼宕?null 閻炴稏鍔庨妵姘€掗崨顖楁晞鐟滅増鎸告晶鐘炽亜閸︻厽绐?
     */
    void switchProjectForTest(Project project) {
        switchProject(project);
    }

    /**
     * 闂侇偄顦懙鎴﹀箰閸パ呮毎濞寸姾顕ф慨鐔兼晬鐏炶偐杩旈柛姘嫰鐎垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓鎯勭€涙ê澶嶉悷鏇炴濞插﹦绱撻弽顒傚竼闁烩晝顭堥崣褔宕氶崱妯绘殰闁?
     *
     * @param task 闁烩晩鍠楅悥锝嗙鐠囨彃顫?
     */
    void selectTaskForTest(Task task) {
        selectTask(task);
    }

    /**
     * 閻熸瑱绠戣ぐ鍌涚┍濠靛棛鎽犳繛缈犺兌閳诲ジ鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粓寮鈾€鏋呭ǎ鍥ㄧ箓閻°劑宕ユ惔锝嗙暠闁绘鍩栭埀顑挎缁楀苯顩奸妷锕€澶嶉悹瀣暟閺併倝濡?
     */
    void saveTasksForTest() {
        onSaveTasks();
    }

    /**
     * 闁告帒娲﹀畷鎻掝啅閹绘帞鏆氶柟瀛樺姇閸ㄥ海绱掗崟顐ゆ綌鐎殿喒鍋撻柣妯垮煐閳ь兛绶ょ槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕鐒﹂弻鍥╂嚊閳ь剟鎮╅懜纰樺亾娴ｇ鐎奸柟璇℃線缁楀瀵煎顒€顨涢柛婵嗙Т缂嶅宕滃鍫綊闁搞儰鍕橀埀?     */
    void toggleCompletedSectionForTest() {
        toggleCompletedSection();
        applySearchFilter();
    }

    /**
     * 闁告帒娲﹀畷鍙夈亜閸︻厽绐楀〒姘€鍕焿閻熸洖妫涘ú濠勪沪閸屾稒鈻旂紒鈧搹鐟靶﹂柟顑跨筏缁辨繃绗熷☉娆戙偞閻犲洦娲熼埞宥夊礉閵婏妇鈧剛浜歌箛鏇犲炊闁告瑱绲煎锔界閹哄鍋?     */
    void toggleSidebarOverlayForTest() {
        toggleSidebarOverlay();
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喖宕氬Δ鍕┾偓鍐磼閸曨亝顐介柨娑樺缁剁敻宕ョ仦钘夌樁婵炴潙顑堥惁顖炴儎鐎涙ê澶嶅鐟板船婵晠骞忛弽銊ヮ伡濞存嚎鍊撶花浼村Υ?     *
     * @return 鐟滅増鎸告晶鐘崇鐠囨彃顫ら柛鎺擃殙閵嗗啰绱掗崟顏咁偨
     */
    TaskListWidget getTaskListWidgetForTest() {
        return taskListWidget;
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柡宥呮喘椤ｅ€熴亹閹惧啿顤呴柡鍕靛灠閹線宕楁担绛嬪晠闁烩晛鐡ㄧ敮瀵哥磽閺嶎剛甯嗛柨娑樺缁堕潧霉鐎ｎ厾妲稿Δ鐘茬焷閻﹀鍨惧鍛化闁告垼顕ч幃妤佹交濞戞ê寮崇紓鍌涚墳缁额偊骞€娴ｆ祴鍋撳┑濠庡殧濞戞柨顦埀?     *
     * @return true 閻炴稏鍔庨妵姘辨嫚閿旇棄鍓伴柡宥呮喘椤ｈ棄顔忛懠鍓佺闁稿繈鍎崇槐顏呮綇閹寸偐鍋?     */
    boolean isDetailTitleEditableForTest() {
        return detailDraft != null && detailDraft.titleEditing;
    }

    /**
     * 閻熸瑱绠戣ぐ鍌滄嫚閿旇棄鍓伴柡宥呮喘椤ｈ姤娼诲☉妯哄汲缂傚倹鐗炵欢顐﹀箑娓氬﹦绀夊〒姘⊕缁佸鎷犻弴鐔屼線骞忛悢鍝勪化闁告垹绮悥锝嗭紣濡吋鐣遍悶娑樺鐠愮喖濡?     */
    void beginDetailTitleEditingForTest() {
        beginDetailTitleEditing();
    }

    /**
     * 閻熸瑱绠戣ぐ鍌滄嫚閿旇棄鍓伴柟鎯版閻粙宕楅幎鑺ワ紨闁圭顦甸幐鎶芥晬鐏炶偐杩旀繛鏉戭儓閻︻垱顨ュ畝鍐闁规儼妫勯惇浠嬪绩閹増宕抽梺顐ｆ缁额偊濡?     */
    void clickDetailCloseButtonForTest() {
        if (detailCloseButton != null) {
            detailCloseButton.onPress();
        }
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柟鎯版閻粙宕楅幎鑺ワ紨闁圭顦甸幐鎶芥儍閸曨喚鐝堕柣锝呯焿缁辨繃绗熷☉娆戙偞閻犲洦娲熼悰娆戞嫚娴ｅ摜顏撮悘鐐╁亾闁?     *
     * @return 闁稿繑濞婂Λ鎾箰婢舵劖灏﹂弶鍫濇贡閺咁偊寮幍顔剧煁
     */
    int[] getDetailCloseButtonBoundsForTest() {
        return toWidgetBounds(detailCloseButton);
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柡宥呮喘椤ｈ姤娼忛幘鍐插汲婵℃妫涘▓鎴炴綇閸︻厽娅曢柨娑樺缁堕潧霉鐎ｎ厾妲稿Δ鐘茬焷閻﹀鏁崘銊ф拱闁?     *
     * @return 闁哄秴娲。鑺ユ綇閹惧啿寮虫俊妤€妫滅粩鐔兼偩鐏炵偓娈剁紓?     */
    int[] getDetailTitleFieldBoundsForTest() {
        return toWidgetBounds(titleField);
    }

    /**
     * 閺夆晜鏌ㄥú鏍紣閸℃绲块柟绋款樀閹稿疇銇愰幘鍐差枀闁哄嫷鍨伴幆渚€宕ｉ婵愭綄闁?     *
     * @return true 閻炴稏鍔庨妵姘紣閸℃绲块柟绋款樀閹告娊宕ｉ婵愭綄
     */
    boolean isClaimButtonVisibleForTest() {
        return claimButton != null && claimButton.visible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍绩閹呯＞闁圭顦甸幐瀹犮亹閹惧啿顤呴柡鍕靛灠閹線宕ｉ婵愭綄闁?     *
     * @return true 閻炴稏鍔庨妵姘跺绩閹呯＞闁圭顦甸幐鎶藉矗椤栨繍娼?
     */
    boolean isAbandonButtonVisibleForTest() {
        return abandonButton != null && abandonButton.visible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍灳濠婂嫬鐦规繛鎻掑綖缁剚绂嶇悰鈾€鍋撳┑鍥х樆闂佺瓔鍠栫紞瀣礈瀹ュ棙笑闁告熬绠戣ぐ鑼喆娴ｇ鍋?     *
     * @return true 閻炴稏鍔庨妵姘跺箰閸ャ劍鐑﹀ù鐘崇墧濮瑰骞愭径鎰唉闁告瑯鍨甸～?
     */
    boolean isAssignOthersButtonVisibleForTest() {
        return assignOthersButton != null && assignOthersButton.visible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍紣閸℃绲块柟绋款樀閹告娊鎯冮崟顔剧彾闁伙絽鐭夌槐婵囩瑹濞戞瑧銈撮悹鍥ㄦ礋閻涙瑧鎷犳担鐑樻；闁告碍鍨电粩椋庝沪閳ь剟濡?     *
     * @return 濡澘妫楄ぐ鍥箰婢舵劖灏﹂弶鍫濇贡閺咁偊寮幍顔剧煁
     */
    int[] getClaimButtonBoundsForTest() {
        return toWidgetBounds(claimButton);
    }

    /**
     * 閺夆晜鏌ㄥú鏍绩閹呯＞闁圭顦甸幐鎶芥儍閸曨喚鐝堕柣锝呯焿缁辨繃绗熷☉娆戙偞閻犲洦娲熼悰娆戞嫚娴ｇ儤妫婚柛姘灥缁旈浠﹂埀顒勫Υ?     *
     * @return 闁衡偓閹呯＞闁圭顦甸幐铏綇閸︻厽娅曢柡浣瑰缁?
     */
    int[] getAbandonButtonBoundsForTest() {
        return toWidgetBounds(abandonButton);
    }

    /**
     * 閺夆晜鏌ㄥú鏍灳濠婂嫬鐦规繛鎻掑綖缁剚绂嶇悰鈾€鍋撳┑鍥х樆闂佺瓔鍠氬▓鎴炴綇閸︻厽娅曢柨娑樺缁堕潧霉鐎ｎ厾妲稿Δ鐘茬焷閻﹀鐥棃娑欏€婚悽顖氬暙閻剟濡?     *
     * @return 闁圭娲﹀ǎ铏閺嶏附鐪介柟绋款樀閹歌櫕娼忛崷顓熸珪闁轰焦澹嗙划?
     */
    int[] getAssignOthersButtonBoundsForTest() {
        return toWidgetBounds(assignOthersButton);
    }

    /**
     * 闁瑰灚鎸哥槐鎴﹀箰閸パ呮毎濞寸姾顕ф慨鐔兼儍閸曨亞鐟愬☉鎾愁儐閺嬪啴鎳ｅ鍐ㄧ闁挎稑濂旂欢鐢稿触鐏炶棄鐦舵繛鏉戭儓閻︻垱绂掗敐鍥╁灣闁哄偆鍙€閳诲牓鎳ｅ鍐ㄧ閻炴稑濂旂拹鐔煎Υ?
     *
     * @param task 闁烩晩鍠楅悥锝嗙鐠囨彃顫?
     */
    void openTaskContextMenuForTest(Task task) {
        openTaskContextMenu(task, 32, 32);
    }

    /**
     * 闁绘劗鎳撻崵顕€骞愰崶褏鏆扮紒渚垮灩缁扁晠鎯冮崟顏嗙憪濞戞挸顑嗛弸鍐嚕濠婂啫绀嬪銈囨缁辨繃绗熷☉妯诲€遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕绶氶埞宥夊礉閵娿劌缍呴柛妤佹礀婵晜鎷呭┃搴撳亾?
     *
     * @param index 闁兼寧绮屽畷鐔搞亜閸︻厼鍋嶇€?
     */
    void clickContextMenuItemForTest(int index) {
        if (!hasContextMenu() || index < 0 || index >= contextMenuItems.size()) {
            closeTaskContextMenu();
            return;
        }
        ContextMenuItem item = contextMenuItems.get(index);
        if (item.enabled && item.action != null) {
            item.action.run();
        } else {
            closeTaskContextMenu();
        }
    }

    /**
     * 闁告帗绋戠紓鎾寸鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊闁挎稑濂旂欢鐢稿触鐏炶棄鐦舵繛鏉戭儓閻︻垱绂掗敐鍥╁灣閻熸洖妫涘ú濠囨偝閳轰緡鍟€闁告帒妫濋崢銈吤规担琛℃煠闁?
     *
     * @param task 闁烩晩鍠楅悥锝嗙鐠囨彃顫?
     * @return 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     */
    Screen createAssignPlayerScreenForTest(Task task) {
        return new AssignPlayerScreen(this, task);
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊濞戞搩鍘惧▓鎴﹀磹濞嗘挴鍋撴径灞借礋閻庣娉涢幃鏇犵矓閺夋寧褰ラ柣鎾楀秶绀夊〒姘☉閹捇宕犻崨顔俱偞閻犲洦娲戦崬顒勬儘娴ｈ鐒介悷灏佸亾閺夆晛娲﹂幎銈囩磼閹惧浜柕?
     *
     * @param screen 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     * @return 闁稿﹥鐟╅埀顒€顦辩敮铏光偓纭呮硾閹洜绮旈弶鎸庡渐闁?
     */
    List<String> getAssignablePlayerNamesForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen) || assignPlayerScreen.filteredMembers == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (AssignableMember member : assignPlayerScreen.filteredMembers) {
            if (member != null && member.displayName != null && !member.displayName.isEmpty()) {
                names.add(member.displayName);
            }
        }
        return List.copyOf(names);
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊鐟滅増鎸告晶鐘绘儍閸曨剛娉婇柛鏂诲妼娴滃摜绮旀导娆戠濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担鍦ⅰ濡ょ姴鏈划鎾礉閵娿劎鐝堕柣锝呰閳?     *
     * @param screen 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     * @return 鐟滅増鎸告晶鐘差煥濮橆剙袟闁稿绻掍簺
     */
    int getAssignPlayerScrollOffsetForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getScrollOffsetForTest();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊鐟滅増鎸告晶鐘诲矗椤栨繍娼岄柣銊ュ閸ㄦ岸宕ㄥΟ娆炬斀闁轰礁搴滅槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕娴囬鍝ョ不濡や焦浠樺鍫嗗嫮娉婇柛鏂诲姀鐎垫牠宕跺ǎ顑藉亾?     *
     * @param screen 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     * @return 鐟滅増鎸告晶鐘诲矗椤栨繍娼岄柟瀛樺姇閹插磭鎮扮仦鐐
     */
    int getAssignPlayerVisibleRowsForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getVisibleRowsForTest();
    }

    /**
     * 婵☆垪鍓濈€氭瑥顭ㄥ宕囨瀭婵犲﹥鑹炬慨鈺傜鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊濞戞搩鍘惧▓鎴﹀箣閹邦剚鍠呴柛鎺擃殙閵嗗啴鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粍銇欓崡鐐残楅柛鎺擃殙閵嗗啫顭ㄥ顒€袟闁?     *
     * @param screen 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     * @param steps 婵犲﹥鑹炬慨鈺侇潰閵夛附娈堕柨娑欑⊕椤掓粓寮幏灞烩偓鍐矆閸濆嫭鍊诲☉鎾愁儐缁挳宕濋妸銉ョ仚閻?     */
    void scrollAssignPlayerListForTest(Screen screen, int steps) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return;
        }
        double listCenterX = assignPlayerScreen.getListCenterXForTest();
        double listCenterY = assignPlayerScreen.getListCenterYForTest();
        int totalSteps = Math.max(0, steps);
        for (int i = 0; i < totalSteps; i++) {
            assignPlayerScreen.mouseScrolled(listCenterX, listCenterY, -1.0D);
        }
    }

    /**
     * 闁告碍鍨抽幑銏ゅ礉閳ュ啿鐎婚梺鏉跨Т閼村﹦绮ｅΔ鍐╃暠闁瑰吋绮庨崒銊ヮ浖閸℃鏅搁柛蹇嬪劚閸炲鈧湱娅㈢槐婵囩瑹濞戞ɑ鍊遍柛鏍ф噺缁佸鎷犻弴姘暕闁活喕绶氶埞宥夊礉閵娿儮鍋撳▎鎾亾婢跺鐪介弶鈺佹处閹躲倝濡?     *
     * @param screen 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     * @param value 闁瑰吋绮庨崒銊╁礂閹惰姤鏆涢悗?
     */
    void setAssignPlayerSearchForTest(Screen screen, String value) {
        if (screen instanceof AssignPlayerScreen assignPlayerScreen && assignPlayerScreen.searchField != null) {
            assignPlayerScreen.searchField.setValue(value == null ? "" : value);
        }
    }

    /**
     * 闁绘劗鎳撻崵顔界鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊濞戞搩鍘惧▓鎴﹀箰閸パ呮毎闁稿﹥鐟╅埀顒€顦Ч澶屾偘瀹€瀣濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆笟鈧埞宥夊礉閵娿儱鐎婚梺鏉跨Т婵晜鎷呭┃搴撳亾?
     *
     * @param screen 濞寸姾顕ф慨鐔煎礆閸℃稑甯崇€殿喖婀遍悰?
     * @param rowIndex 闁稿﹥鐟╅埀顒€顦Ч澶屾偘瀹€鈧崒銊ヮ嚕?
     */
    void clickAssignPlayerRowForTest(Screen screen, int rowIndex) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen) || assignPlayerScreen.playerButtons == null) {
            return;
        }
        if (rowIndex < 0 || rowIndex >= assignPlayerScreen.playerButtons.length) {
            return;
        }
        Button button = assignPlayerScreen.playerButtons[rowIndex];
        if (button != null && button.active && button.visible) {
            button.onPress();
        }
    }

    @Override
    protected void init() {
        super.init();
        openedStorageNamespace = DataPathProvider.getStorageNamespace();

        // Initialize task manager and load tasks from storage
        if (personalTaskManager == null) {
            personalTaskManager = new TaskManager();
            try {
                List<Task> loadedTasks = ClientTaskStorageHelper.loadPersonalTasks(TodoListCommon.getTaskStorage(), this.minecraft);
                for (Task task : loadedTasks) {
                    personalTaskManager.addTask(task);
                }
                TodoConstants.LOGGER.info("Loaded {} tasks from storage", loadedTasks.size());
            } catch (Exception e) {
                TodoConstants.LOGGER.error("Failed to load tasks from storage", e);
            }
        }

        teamTaskManager = ClientBridge.ops().getTeamTaskManager();
        
        // Initialize ProjectManager
        projectManager = TodoListCommon.getProjectManager();
        projectManager.addListener(this);
        teamProjectsEnabled = ClientBridge.ops().isTeamProjectsEnabled();
        if (!teamProjectsEnabled) {
            projectScopeFilter = Project.Scope.PERSONAL;
            if (currentProject != null && currentProject.getScope() == Project.Scope.TEAM) {
                currentProject = null;
            }
            viewMode = ViewMode.PERSONAL;
        }

        applyLastGuiState();
        
        // Verify currentProject is still valid
        if (currentProject != null) {
            Project p = projectManager.getProject(currentProject.getId());
            if (p == null) {
                currentProject = null; // Project was deleted
            } else {
                currentProject = p; // Update reference to fresh object
            }
        }

        if (currentProject == null) {
            currentProject = getPreferredProjectForScope(projectScopeFilter);
        }
        if (currentProject != null) {
            rememberSelectedProject(currentProject);
            projectScopeFilter = currentProject.getScope();
        }
        syncViewStateForCurrentProject();
        
        // Ensure taskManager matches currentProject
        if (currentProject != null) {
            if (currentSpaceMode == SpaceMode.PERSONAL) {
                taskManager = personalTaskManager;
            } else {
                taskManager = teamTaskManager;
            }
        } else {
            taskManager = personalTaskManager;
        }

        syncHudViewForProject(currentProject);
        syncActiveProjectIdWithCurrentProject();
        hasUnsavedChanges = (viewMode == ViewMode.PERSONAL) ? personalHasUnsavedChanges : teamHasUnsavedChanges;

        rebuildUI();
    }

    @Override
    public void removed() {
        super.removed();
        saveLastGuiState();
        if (projectManager != null) {
            projectManager.removeListener(this);
        }
    }

    /**
     * 闁哄秷顫夊畵浣姐亹閹惧啿顤呭銈呮贡濞茬増绋夋惔銏★紜閻熸瑥妫楀ù妯何熼垾宕囩闁告艾鏈鐐哄棘閹殿喗鐣辩紒灞炬そ濡寧绋夋惔婵囧床闁告枀銈庢綊闁搞儱澧芥慨鎼佸箑娴ｇ鍋?     */
    private void syncViewStateForCurrentProject() {
        currentSpaceMode = resolveSpaceMode(currentProject);
        List<TaskViewOption> visibleOptions = buildVisibleViewOptions(currentSpaceMode);
        TaskViewOption resolvedOption = currentSpaceMode == SpaceMode.TEAM && viewMode == ViewMode.PERSONAL
                ? resolveDefaultViewForSpace(currentSpaceMode)
                : resolveTaskViewOptionFromLegacy(viewMode);
        if (!visibleOptions.contains(resolvedOption)) {
            resolvedOption = resolveDefaultViewForSpace(currentSpaceMode);
        }
        currentTaskViewOption = resolvedOption;
        syncLegacyViewModeFromState();
    }

    /**
     * 闁哄秷顫夊畵浣广亜閸︻厽绐楅悷娆欑稻閻庡€熴亹閹惧啿顤呯紒灞炬そ濡灝螣閳ュ磭纭€闁?     *
     * @param project 鐟滅増鎸告晶鐘炽亜閸︻厽绐?
     * @return 閻熸瑱绲鹃悗浠嬪触鎼达絾鐣辩紒灞炬そ濡灝螣閳ュ磭纭€
     */
    private SpaceMode resolveSpaceMode(Project project) {
        if (project == null || project.getScope() == Project.Scope.PERSONAL || !teamProjectsEnabled) {
            return SpaceMode.PERSONAL;
        }
        return SpaceMode.TEAM;
    }

    /**
     * 闁哄瀚紓鎾广亹閹惧啿顤呯紒灞炬そ濡寧绋夌€ｎ亜璁查悷娆庤兌濞堟垶绂掔拠鎻掝潳閻熸瑥妫楀ù姗€鏌呮径鎰┾偓宥夊Υ?     *
     * @param spaceMode 鐟滅増鎸告晶鐘电矚濞差亝锛熸俊顖椻偓宕囩
     * @return 鐟滅増鎸告晶鐘电矚濞差亝锛熷☉鎾愁儏瑜拌尙鎲存担鐑樼暠濞寸姾顕ф慨鐔烘喆閸℃绂堥梺顐㈩樀閵?
     */
    private List<TaskViewOption> buildVisibleViewOptions(SpaceMode spaceMode) {
        if (spaceMode == SpaceMode.TEAM) {
            return List.of(TaskViewOption.UNASSIGNED, TaskViewOption.ALL, TaskViewOption.MY);
        }
        return List.of(TaskViewOption.MY);
    }

    /**
     * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呯紒灞炬そ濡寧绋夌€ｎ剚鐣卞娑欘焾椤撶粯绂掔拠鎻掝潳閻熸瑥妫楀ù姗€鏌呮径鎰┾偓宥夊Υ?     *
     * @param spaceMode 鐟滅増鎸告晶鐘电矚濞差亝锛熸俊顖椻偓宕囩
     * @return 濮掓稒顭堥缁樼鐠囨彃顫ら悷娆忔濞存﹢鏌呮径鎰┾偓?
     */
    private TaskViewOption resolveDefaultViewForSpace(SpaceMode spaceMode) {
        return spaceMode == SpaceMode.TEAM ? TaskViewOption.UNASSIGNED : TaskViewOption.MY;
    }

    /**
     * 閻忓繐妫欏Λ顐︽儍閸曨噮娼掗柛銉у亾鑶╃€殿喖绻戝Σ褏浜搁崟顐㈢厒闁哄倹澹嗗▓鎴炵鐠囨彃顫ら悷娆忔濞存﹢鏌呮径鎰┾偓宥夊Υ?     *
     * @param legacyViewMode 闁哄唲鍛暠閻熸瑥妫楀ù妯何熼垾宕囩
     * @return 閻庣數鎳撶花鏌ユ儍閸曨剚鐓€濞寸姾顕ф慨鐔烘喆閸℃绂堥梺顐㈩樀閵?
     */
    private TaskViewOption resolveTaskViewOptionFromLegacy(ViewMode legacyViewMode) {
        if (legacyViewMode == ViewMode.TEAM_UNASSIGNED) {
            return TaskViewOption.UNASSIGNED;
        }
        if (legacyViewMode == ViewMode.TEAM_ALL) {
            return TaskViewOption.ALL;
        }
        return TaskViewOption.MY;
    }

    /**
     * 閻忓繐妫欓弻濠囨儍閸曨厸鏁勯梻鍌滅節缁楀本绂掔拠鎻掝潳閻熸瑥妫楀ù姗€鎮╅懜纰樺亾娴ｇ鍐€闁告碍鍨甸幃鎾愁潰閵夈儱鐓傞柡鍐勫懏鐣遍悷娆忔濞存ê螣閳ュ磭纭€闁?     */
    private void syncLegacyViewModeFromState() {
        if (currentSpaceMode == SpaceMode.PERSONAL) {
            viewMode = ViewMode.PERSONAL;
            return;
        }
        if (currentTaskViewOption == TaskViewOption.ALL) {
            viewMode = ViewMode.TEAM_ALL;
        } else if (currentTaskViewOption == TaskViewOption.UNASSIGNED) {
            viewMode = ViewMode.TEAM_UNASSIGNED;
        } else {
            viewMode = ViewMode.TEAM_ASSIGNED;
        }
    }

    /**
     * 闁告帒娲﹀畷鎻掝啅閹绘帞鏆氶柟瀛樺姇閸ㄥ海绱掗崟顐ゆ綌鐎殿喒鍋撻柣妯垮煐閳ь兛闄嶉埀?     */
    private void toggleCompletedSection() {
        completedExpanded = !completedExpanded;
    }

    private void applyLastGuiState() {
        if (lastGuiState == null || projectManager == null) return;

        projectScopeFilter = lastGuiState.projectScopeFilter == null ? Project.Scope.PERSONAL : lastGuiState.projectScopeFilter;
        if (!teamProjectsEnabled) {
            projectScopeFilter = Project.Scope.PERSONAL;
        }

        if (lastGuiState.projectSearchQuery != null) {
            projectSearchQuery = lastGuiState.projectSearchQuery;
        }
        preferredPersonalProjectId = lastGuiState.lastPersonalProjectId;
        preferredTeamProjectId = lastGuiState.lastTeamProjectId;
        completedExpanded = lastGuiState.completedExpanded;

        if (lastGuiState.viewMode != null) {
            viewMode = lastGuiState.viewMode;
        }
        if (lastGuiState.spaceMode != null) {
            currentSpaceMode = lastGuiState.spaceMode;
        }
        if (lastGuiState.taskViewOption != null) {
            currentTaskViewOption = lastGuiState.taskViewOption;
        }
        if (!teamProjectsEnabled && viewMode != ViewMode.PERSONAL) {
            viewMode = ViewMode.PERSONAL;
        }

        currentPriorityFilter = lastGuiState.currentPriorityFilter;
        currentFilter = "active";
        if (lastGuiState.searchQuery != null) {
            searchQuery = lastGuiState.searchQuery;
        }

        if (lastGuiState.currentProjectId != null && !lastGuiState.currentProjectId.isEmpty()) {
            Project p = projectManager.getProject(lastGuiState.currentProjectId);
            if (p != null && (teamProjectsEnabled || p.getScope() == Project.Scope.PERSONAL)) {
                currentProject = p;
                projectScopeFilter = p.getScope();
                rememberSelectedProject(p);
            }
        }
    }

    private void saveLastGuiState() {
        rememberSelectedProject(currentProject);
        LastGuiState s = new LastGuiState();
        s.projectScopeFilter = projectScopeFilter;
        s.currentProjectId = currentProject == null ? null : currentProject.getId();
        s.viewMode = viewMode;
        s.spaceMode = currentSpaceMode;
        s.taskViewOption = currentTaskViewOption;
        s.completedExpanded = completedExpanded;
        s.currentPriorityFilter = currentPriorityFilter;
        s.currentFilter = currentFilter;
        s.searchQuery = searchQuery;
        s.projectSearchQuery = projectSearchQuery;
        s.lastPersonalProjectId = preferredPersonalProjectId;
        s.lastTeamProjectId = preferredTeamProjectId;
        lastGuiState = s;
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (this.minecraft == null) return;
        this.minecraft.execute(() -> {
            if (type == ProjectManager.ProjectChangeType.CLEARED) {
                switchProject(null);
                return;
            }

            if (project == null) return;

            if (type == ProjectManager.ProjectChangeType.REMOVED) {
                if (!TodoListCommon.isProjectSyncInProgress()) {
                    hardDeleteTasksForDeletedProject(project);
                }
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    switchProject(resolveFallbackProjectAfterRemoval(project));
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.ADDED) {
                if (currentProject == null) {
                    switchProject(resolvePreferredProjectForCurrentScope());
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.UPDATED) {
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    currentProject = project;
                }
                refreshAfterProjectMutation();
            }
        });
    }

    /**
     * 濡炪倕婀卞ú鐗堟櫠閻愭彃鐏╅柡鈧悷鐗堝€甸柟绗涘棭鏀介弶鐐差煼閸ｆ椽宕氶柨瀣厐闁挎稑鐭傛导鈺呭礂瀹ュ棙娈诲銈囨暬閸ｆ悂寮弶鍨仴濠殿喖顑呯€垫煡濡?
     */
    private void refreshAfterProjectMutation() {
        syncActiveProjectIdWithCurrentProject();
        updateProjectList();
        refreshTaskList();
        updateButtonStates();
        updateProjectActionButtons();
    }

    /**
     * 閻?activeProjectId 濞戞挸楠哥紞瀣礈瀹ュ鈧秹鎯勯鎯﹂柟顑跨椤曨喗顬囬幇鍓佺闂侇剙鐏濋崢銈呪枔鐎ｎ剚娈屽鎯伴哺閺呫儲銇勯崷顓熺獥 ID闁?
     */
    private void syncActiveProjectIdWithCurrentProject() {
        if (currentProject == null || projectManager == null) {
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            return;
        }
        Project fresh = projectManager.getProject(currentProject.getId());
        if (fresh == null) {
            currentProject = null;
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            return;
        }
        currentProject = fresh;
        ClientBridge.ops().setActiveProjectId(currentProject.getId());
        ClientBridge.ops().sendSetActiveProjectId(currentProject.getId());
        ClientBridge.saveLastActiveProjectId(currentProject.getId());
    }

    /**
     * 鐟滅増鎸告晶鐘炽亜閸︻厽绐楅悶姘煎亜閸ㄥ綊姊介妶鍡橆槯闁挎稑鏈€垫粏銇愰幘鍐差枀 scope 濞村吋锚閸樻盯鏌呮径瀣仴濞戞挴鍋撳☉鎿冧簻瑜版煡鎮介妸鈹库偓宥夋儎椤曞棛绀夐柛蹇庢祰椤斿繑绋夐搹鍏夋晞闁?
     */
    private Project resolveFallbackProjectAfterRemoval(Project removedProject) {
        Project preferred = resolvePreferredProjectForCurrentScope();
        if (preferred != null) {
            return preferred;
        }
        if (removedProject != null) {
            Project.Scope fallbackScope = removedProject.getScope() == Project.Scope.PERSONAL ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            return getPreferredProjectForScope(fallbackScope);
        }
        return null;
    }

    /**
     * 闁圭顦紞瀣礈?scope 闂侇偄顦扮€氥劍锛冮弽顓涘亾婢舵劑鈧秹鎯勯鍡欑濞戞挸绉磋ぐ鏌ユ偨閵婏附顦ч柛銉у仱閳ь兘鍋撻柛鎺撴緲瑜扮喐绋夐埀?scope闁?
     */
    private Project resolvePreferredProjectForCurrentScope() {
        Project preferred = getPreferredProjectForScope(projectScopeFilter);
        if (preferred != null) {
            return preferred;
        }
        Project.Scope fallbackScope = projectScopeFilter == Project.Scope.PERSONAL ? Project.Scope.TEAM : Project.Scope.PERSONAL;
        return getPreferredProjectForScope(fallbackScope);
    }

    private void rebuildUI() {
        syncViewStateForCurrentProject();
        currentFilter = "active";
        if (searchQuery == null) {
            searchQuery = "";
        }
        if (projectSearchQuery == null) {
            projectSearchQuery = "";
        }
        baseFilteredTasks = new ArrayList<>();
        filteredTasks = new ArrayList<>();
        this.clearWidgets();

        ModConfig config = ModConfig.getInstance();
        responsiveTier = resolveResponsiveTier(this.width, this.height);
        syncOverlayStateForResponsiveTier();

        layoutMetrics = buildMainLayoutMetrics(config);
        LayoutRect sidebarBounds = layoutMetrics.sidebarBounds;
        LayoutRect contentBounds = layoutMetrics.contentBounds;
        LayoutRect detailBounds = layoutMetrics.detailBounds;
        int padding = layoutMetrics.padding;
        int panelGap = layoutMetrics.gap;
        int topBarGap = clampInt(config.getElementSpacing(), 4, 12);
        int topBarY = contentBounds.y + 14;
        int topBarHeight = 20;
        int secondRowY = topBarY + topBarHeight + 18;
        int secondRowHeight = 20;
        int inputRowHeight = 20;
        int inputRowY = contentBounds.y + contentBounds.height - inputRowHeight - 10;
        int listTop = secondRowY + secondRowHeight + topBarGap;
        int listBottom = inputRowY - Math.max(8, topBarGap + 2);
        int listHeight = Math.max(0, listBottom - listTop);

        int sidebarTopY = sidebarBounds.y;
        int sidebarWidth = sidebarBounds.width;
        int contentX = contentBounds.x;
        int contentWidth = contentBounds.width;
        int rightPanelX = detailBounds.x;
        int rightPanelWidth = detailBounds.width;

        int overlayToggleWidth = layoutMetrics.sidebarOverlay ? 56 : 0;
        sidebarToggleButton = Button.builder(Component.translatable("gui.todolist.project.sidebar"), b -> toggleSidebarOverlay())
                .bounds(contentX, topBarY, overlayToggleWidth, topBarHeight).build();
        sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
        sidebarToggleButton.active = layoutMetrics.sidebarOverlay;
        this.addRenderableWidget(sidebarToggleButton);

        int actionGap = 6;
        int configButtonWidth = Math.max(52, Math.min(72, this.font.width(Component.translatable("gui.todolist.config.title")) + 14));
        int cancelButtonWidth = Math.max(52, Math.min(72, this.font.width(Component.translatable("gui.todolist.cancel")) + 14));
        int saveButtonWidth = Math.max(52, Math.min(72, this.font.width(Component.translatable("gui.todolist.save")) + 14));
        int configButtonX = contentX + contentWidth - configButtonWidth;
        int cancelButtonX = configButtonX - actionGap - cancelButtonWidth;
        int saveButtonX = cancelButtonX - actionGap - saveButtonWidth;
        configButton = Button.builder(Component.translatable("gui.todolist.config.title"), b -> this.minecraft.setScreen(new ConfigScreen(this)))
                .bounds(configButtonX, topBarY, configButtonWidth, topBarHeight).build();
        this.addRenderableWidget(configButton);

        int filterGap = 6;
        int filtersX = contentX + (layoutMetrics.sidebarOverlay ? overlayToggleWidth + filterGap : 0);
        int btnH = 20;
        int priorityBtnWidth = Math.min(108, Math.max(64, this.font.width(getPriorityFilterText()) + 16));
        int priorityBtnX = contentX + contentWidth - priorityBtnWidth;
        filterPriorityButton = Button.builder(getPriorityFilterText(), button -> {
            currentPriorityFilter = (currentPriorityFilter + 1) % 4;
            button.setMessage(getPriorityFilterText());
            applyPriorityFilter();
        }).bounds(priorityBtnX, secondRowY, priorityBtnWidth, btnH).build();
        this.addRenderableWidget(filterPriorityButton);

        filterStatusButton = null;
        viewToggleButton = null;

        int searchWidth = Math.max(80, priorityBtnX - filterGap - filtersX);
        searchField = new EditBox(this.font, filtersX, secondRowY, searchWidth, secondRowHeight, Component.empty());
        searchField.setHint(Component.translatable("gui.todolist.input.search.placeholder"));
        searchField.setValue(searchQuery);
        this.addRenderableWidget(searchField);

        int sidebarInset = 8;
        int sidebarInnerX = sidebarBounds.x + sidebarInset;
        int sidebarInnerWidth = Math.max(80, sidebarWidth - sidebarInset * 2);
        int spaceButtonsY = sidebarTopY + 28;
        int spaceButtonGap = 6;
        int spaceButtonWidth = Math.max(48, (sidebarInnerWidth - spaceButtonGap) / 2);
        personalSpaceButton = Button.builder(Component.translatable("gui.todolist.scope.personal"), b -> {
            Project targetProject = getPreferredProjectForScope(Project.Scope.PERSONAL);
            switchProject(targetProject);
        }).bounds(sidebarInnerX, spaceButtonsY, spaceButtonWidth, 20).build();
        this.addRenderableWidget(personalSpaceButton);

        teamSpaceButton = Button.builder(Component.translatable("gui.todolist.scope.team"), b -> {
            if (!teamProjectsEnabled) {
                return;
            }
            Project targetProject = getPreferredProjectForScope(Project.Scope.TEAM);
            switchProject(targetProject);
        }).bounds(sidebarInnerX + spaceButtonWidth + spaceButtonGap, spaceButtonsY,
                sidebarInnerWidth - spaceButtonWidth - spaceButtonGap, 20).build();
        teamSpaceButton.active = teamProjectsEnabled;
        this.addRenderableWidget(teamSpaceButton);

        int viewButtonsY = spaceButtonsY + 32;
        myViewButton = Button.builder(Component.literal("\u6211\u7684"), b -> {
            if (currentSpaceMode == SpaceMode.PERSONAL) {
                switchView(ViewMode.PERSONAL);
                return;
            }
            switchView(ViewMode.TEAM_ASSIGNED);
        }).bounds(sidebarInnerX, viewButtonsY, sidebarInnerWidth, 20).build();
        this.addRenderableWidget(myViewButton);

        unassignedViewButton = Button.builder(Component.literal("\u5f85\u5206\u914d"), b -> switchView(ViewMode.TEAM_UNASSIGNED))
                .bounds(sidebarInnerX, viewButtonsY + 24, sidebarInnerWidth, 20).build();
        this.addRenderableWidget(unassignedViewButton);

        allViewButton = Button.builder(Component.translatable("gui.todolist.all"), b -> switchView(ViewMode.TEAM_ALL))
                .bounds(sidebarInnerX, viewButtonsY + 48, sidebarInnerWidth, 20).build();
        this.addRenderableWidget(allViewButton);

        int projectSearchY = currentSpaceMode == SpaceMode.TEAM ? viewButtonsY + 86 : viewButtonsY + 38;
        projectSearchField = new EditBox(this.font, sidebarInnerX, projectSearchY, sidebarInnerWidth, 20, Component.translatable("gui.todolist.project.search"));
        projectSearchField.setHint(Component.translatable("gui.todolist.project.search"));
        projectSearchField.setValue(projectSearchQuery);
        projectSearchField.setResponder(text -> {
            projectSearchQuery = text;
            updateProjectList();
        });
        this.addRenderableWidget(projectSearchField);

        int projectButtonGap = 4;
        int projectButtonWidth = Math.max(28, (sidebarInnerWidth - projectButtonGap * 2) / 3);
        int projectButtonsY = sidebarBounds.y + sidebarBounds.height - 20;
        int projectListY = projectSearchY + 28;
        int projectListHeight = Math.max(0, projectButtonsY - 8 - projectListY);

        projectListWidget = new ProjectListWidget(this.minecraft, sidebarInnerX, projectListY, sidebarInnerWidth, projectListHeight);
        updateProjectList();
        projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        projectListWidget.setSelectedProject(currentProject);
        projectListWidget.setOnProjectSelected(this::switchProject);

        addProjectBtn = Button.builder(Component.translatable("gui.todolist.add"), b -> onAddProject())
                .bounds(sidebarInnerX, projectButtonsY, projectButtonWidth, 20).build();
        this.addRenderableWidget(addProjectBtn);

        editProjectBtn = Button.builder(Component.translatable("gui.todolist.edit"), b -> onProjectSettings())
                .bounds(sidebarInnerX + projectButtonWidth + projectButtonGap, projectButtonsY, projectButtonWidth, 20).build();
        editProjectBtn.active = currentProject != null;
        this.addRenderableWidget(editProjectBtn);

        int deleteButtonX = sidebarInnerX + (projectButtonWidth + projectButtonGap) * 2;
        deleteProjectBtn = Button.builder(Component.translatable("gui.todolist.delete"), b -> onProjectDelete())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, 20).build();
        this.addRenderableWidget(deleteProjectBtn);

        applyJoinProjectBtn = Button.builder(Component.translatable("gui.todolist.project.join.apply"), b -> onApplyJoinProject())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, 20).build();
        this.addRenderableWidget(applyJoinProjectBtn);
        updateProjectActionButtons();

        taskListWidget = new TaskListWidget(this.minecraft, contentX, listTop, contentWidth, listHeight);
        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        taskListWidget.setTeamAllViewForNonOp(getCurrentRole() == Role.MEMBER && teamAllView);
        taskListWidget.setTaskReorderEnabled(isTaskReorderAllowedInCurrentView());
        taskListWidget.setSections(buildTaskPaneSections());
        if (selectedTask != null) {
            taskListWidget.setSelectedTask(selectedTask);
        }
        taskListWidget.setOnTaskToggleCompletion(task -> {
            if (task.isCompleted()) {
                return;
            }
            boolean wasCompleted = task.isCompleted();
            toggleTaskCompletion(task);
            if (!wasCompleted && task.isCompleted()) {
                addNotification(Component.translatable("message.todolist.completed", task.getTitle()).getString());
                if (config.isEnableSoundEffects() && this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 1.0F);
                }
            }
            refreshTaskList();
        });
        taskListWidget.setOnTaskReorder(this::onManualReorderActiveTasks);

        quickAddField = new EditBox(this.font, contentX, inputRowY, contentWidth, inputRowHeight, Component.empty());
        quickAddField.setHint(Component.translatable("gui.todolist.input.title.placeholder"));
        quickAddField.setValue("");
        quickAddField.setMaxLength(100);
        this.addRenderableWidget(quickAddField);

        int rightInnerPadding = 4;
        int rightFieldWidth = Math.max(60, rightPanelWidth - rightInnerPadding * 2);
        int rightPanelTop = detailBounds.y;
        int rightPanelBottom = detailBounds.y + detailBounds.height;
        int rightSectionGap = 6;
        int textH = this.font.lineHeight;
        int assignButtonWidth = rightFieldWidth;
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        int assignsX = rightPanelX + rightInnerPadding;
        int closeRowY = rightPanelTop + rightInnerPadding;
        int closeButtonSize = 20;
        int closeButtonX = Math.max(assignsX, rightPanelX + rightPanelWidth - rightInnerPadding - closeButtonSize);

        detailCloseButton = Button.builder(Component.literal("X"), b -> clearSelectedTask())
                .bounds(closeButtonX, closeRowY, closeButtonSize, closeButtonSize)
                .build();
        this.addRenderableWidget(detailCloseButton);

        int titleFieldY = closeRowY + closeButtonSize + rightSectionGap;
        titleField = new EditBox(this.font, assignsX, titleFieldY, rightFieldWidth, 20, Component.empty());
        titleField.setHint(Component.translatable("gui.todolist.input.title.placeholder"));
        titleField.setValue("");
        titleField.setMaxLength(100);
        titleField.setEditable(false);
        this.addRenderableWidget(titleField);

        int teamButtonsTop = rightPanelBottom;
        if (showAssignButtons) {
            int teamButtonsTotalHeight = assignButtonHeight * 3 + assignButtonGap * 2;
            teamButtonsTop = rightPanelBottom - teamButtonsTotalHeight;
        }

        int tagFieldY = teamButtonsTop - rightSectionGap - 20;
        int minTagFieldY = titleFieldY + 20 + textH + rightSectionGap + 32;
        if (tagFieldY < minTagFieldY) {
            tagFieldY = minTagFieldY;
        }
        int descFieldY = titleFieldY + 20 + textH + 2 + rightSectionGap;
        int descFieldBottom = tagFieldY - rightSectionGap - textH - 2;
        int descFieldHeight = Math.max(28, descFieldBottom - descFieldY);
        descField = new MultiLineEditBox(
                this.font,
                assignsX,
                descFieldY,
                rightFieldWidth,
                descFieldHeight,
                Component.translatable("gui.todolist.input.description"),
                Component.translatable("gui.todolist.input.description.placeholder")
        );
        descField.setValue("");
        descField.setCharacterLimit(2000);
        this.addRenderableWidget(descField);

        tagField = new EditBox(this.font, assignsX, tagFieldY, rightFieldWidth, 20, Component.empty());
        tagField.setValue("");
        tagField.setMaxLength(100);
        this.addRenderableWidget(tagField);

        claimButton = Button.builder(Component.translatable("gui.todolist.claim_task"), b -> onClaimTask())
                .bounds(assignsX, teamButtonsTop, assignButtonWidth, assignButtonHeight).build();
        claimButton.active = false;
        this.addRenderableWidget(claimButton);

        abandonButton = Button.builder(Component.translatable("gui.todolist.abandon_task"), b -> onAbandonTask())
                .bounds(assignsX, teamButtonsTop + (assignButtonHeight + assignButtonGap), assignButtonWidth, assignButtonHeight).build();
        abandonButton.active = false;
        this.addRenderableWidget(abandonButton);

        assignOthersButton = Button.builder(Component.translatable("gui.todolist.assign_others"), b -> onAssignOthers())
                .bounds(assignsX, teamButtonsTop + (assignButtonHeight + assignButtonGap) * 2, assignButtonWidth, assignButtonHeight).build();
        assignOthersButton.active = false;
        this.addRenderableWidget(assignOthersButton);

        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> onSaveTasks())
                .bounds(saveButtonX, topBarY, saveButtonWidth, 20).build();
        this.addRenderableWidget(saveButton);

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onCancel())
                .bounds(cancelButtonX, topBarY, cancelButtonWidth, 20).build();
        this.addRenderableWidget(cancelButton);

        applyResponsiveWidgetVisibility();

        // Listeners
        titleField.setResponder(this::onDetailTitleChanged);
        descField.setValueListener(text -> {
            onDetailDescriptionChanged(text);
        });
        tagField.setResponder(this::onDetailTagsChanged);
        searchField.setResponder(text -> {
            searchQuery = text == null ? "" : text.trim().toLowerCase();
            applySearchFilter();
        });

        filterTasks(currentFilter);
        syncDetailWidgetsFromState();
        this.setFocused(quickAddField);
        updateButtonStates();
    }

    /**
     * 闁哄秷顫夊畵浣虹玻濡も偓瑜版稓鈧€涚矙閻濐喚鎲撮敐鍡欌偓鍊熴亹閹惧啿顤呭☉鎾瑰吹閺咁偊妫冮姀銏＄暠闁告繂绉寸花鎻掝嚕韫囨柣鈧倹鎷呭鍐ｅ亾?     *
     * @param screenWidth 鐟滅増鎸告晶鐘典沪韫囨挾顔庨悗纭呮鐎?
     * @param screenHeight 鐟滅増鎸告晶鐘典沪韫囨挾顔庡Δ鍌浢€?
     * @return 閻熸瑱绲鹃悗浠嬪触鎼达絾鐣遍柛婵嗙Т缁ㄦ彃顕ｈ箛鏂烩偓鍌涙媴?     */
    private ResponsiveTier resolveResponsiveTier(int screenWidth, int screenHeight) {
        ResponsiveTier widthTier = resolveWidthTier(screenWidth);
        ResponsiveTier heightTier = resolveHeightTier(screenHeight);
        return widthTier.ordinal() >= heightTier.ordinal() ? widthTier : heightTier;
    }

    /**
     * 闁圭顦鏃€鎯旈敃浣囨帡寮搁幇顒佹儥閹煎瓨鏌ㄧ槐鈥愁浖閿濆嫮绉撮柕?     *
     * @param screenWidth 鐟滅増鎸告晶鐘典沪韫囨挾顔庨悗纭呮鐎?
     * @return 閻庣妫勭€瑰磭鈧數鎳撶花鏌ユ儍閸曨兙鈧倹鎷?     */
    private ResponsiveTier resolveWidthTier(int screenWidth) {
        if (screenWidth >= 460) {
            return ResponsiveTier.LARGE;
        }
        if (screenWidth >= 380) {
            return ResponsiveTier.MEDIUM;
        }
        if (screenWidth >= 320) {
            return ResponsiveTier.COMPACT;
        }
        return ResponsiveTier.MINIMAL;
    }

    /**
     * 闁圭顦甸悵顔芥償閿曚絿鎺楀几閹邦剚鎯欓幖瀛樻煥缁扁€愁浖閿濆嫮绉撮柕?     *
     * @param screenHeight 鐟滅増鎸告晶鐘典沪韫囨挾顔庡Δ鍌浢€?
     * @return 濡ゅ倹锚鐎瑰磭鈧數鎳撶花鏌ユ儍閸曨兙鈧倹鎷?     */
    private ResponsiveTier resolveHeightTier(int screenHeight) {
        if (screenHeight >= 280) {
            return ResponsiveTier.LARGE;
        }
        if (screenHeight >= 240) {
            return ResponsiveTier.MEDIUM;
        }
        if (screenHeight >= 200) {
            return ResponsiveTier.COMPACT;
        }
        return ResponsiveTier.MINIMAL;
    }

    /**
     * 闁哄秷顫夊畵浣姐亹閹惧啿顤呮俊妤嬬导缂嶅懘宕ョ仦缁㈠妱閻熸洖妫涘ú濠勪沪閸屾氨娼旂€殿喒鍋撻柣妯垮煐閳ь兛绶ょ槐婵囩┍濠靛﹦妲堥悘蹇撶箳閻涖儵宕ｉ敐鍛伌閻忕偐鍋撳☉鎾崇С缁变即骞愰妶鍛瘔閻忕偛绻愮粻鐑藉Υ?     */
    private void syncOverlayStateForResponsiveTier() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            sidebarOverlayVisible = false;
        }
        if (responsiveTier == ResponsiveTier.LARGE || responsiveTier == ResponsiveTier.MEDIUM) {
            detailOverlayVisible = false;
        } else if (selectedTask == null) {
            detailOverlayVisible = false;
        } else {
            detailOverlayVisible = true;
        }
    }

    /**
     * 閻犱緤绱曢悾鏄忋亹閹惧啿顤呭☉鎾瑰吹閺咁偊妫冮姀銏＄暠濞戞挸顦伴悥顔炬暜閸愩劎婀伴弶鍫濇贡閺咁偊濡?     *
     * @param config 鐟滅増鎸告晶鐘绘煀瀹ュ洨鏋傞悗鐢殿攰閽?
     * @return 濞戞捁宕甸弲顐︽閵忕姷顏撮悘鐐╁亾闊浂鍋嗛崣?
     */
    private MainLayoutMetrics buildMainLayoutMetrics(ModConfig config) {
        int padding = resolveLayoutPadding(config);
        int gap = responsiveTier == ResponsiveTier.LARGE ? 10 : 8;
        int panelTop = Math.max(24, padding + 16);
        int bottomBarHeight = 20;
        int inputRowHeight = 20;
        int topBarGap = clampInt(config.getElementSpacing(), 4, 12);
        int bottomBarY = this.height - padding - bottomBarHeight;
        int inputRowY = bottomBarY - topBarGap - inputRowHeight;
        int panelBottom = inputRowY + inputRowHeight;
        int panelHeight = Math.max(0, panelBottom - panelTop);

        boolean sidebarOverlay = responsiveTier == ResponsiveTier.MINIMAL;
        boolean detailOverlay = responsiveTier == ResponsiveTier.COMPACT || responsiveTier == ResponsiveTier.MINIMAL;
        boolean sidebarVisible = !sidebarOverlay || sidebarOverlayVisible;
        boolean detailVisible = selectedTask != null && (!detailOverlay || detailOverlayVisible);

        int availableWidth = Math.max(120, this.width - padding * 2);
        int minContentWidth = switch (responsiveTier) {
            case LARGE -> Math.min(180, Math.max(160, availableWidth));
            case MEDIUM -> Math.min(140, Math.max(132, availableWidth));
            case COMPACT -> Math.min(160, Math.max(136, availableWidth));
            case MINIMAL -> Math.min(150, Math.max(120, availableWidth));
        };
        int sidebarWidth = resolveSidebarWidth(config, availableWidth);
        int detailWidth = resolveDetailWidth(availableWidth);

        if (!sidebarOverlay) {
            int inlineAvailable = availableWidth - gap - (detailVisible && !detailOverlay ? gap : 0);
            int desiredTotal = sidebarWidth + minContentWidth + (detailVisible && !detailOverlay ? detailWidth : 0);
            if (desiredTotal > inlineAvailable) {
                int overflow = desiredTotal - inlineAvailable;
                if (detailVisible && !detailOverlay) {
                    int detailShrink = Math.min(overflow, Math.max(0, detailWidth - 120));
                    detailWidth -= detailShrink;
                    overflow -= detailShrink;
                }
                if (overflow > 0) {
                    int sidebarShrink = Math.min(overflow, Math.max(0, sidebarWidth - 96));
                    sidebarWidth -= sidebarShrink;
                }
            }
        }

        int inlineSidebarWidth = sidebarOverlay ? 0 : sidebarWidth;
        int inlineDetailWidth = detailVisible && !detailOverlay ? detailWidth : 0;
        int contentWidth = availableWidth - inlineSidebarWidth - inlineDetailWidth;
        if (!sidebarOverlay) {
            contentWidth -= gap;
        }
        if (detailVisible && !detailOverlay) {
            contentWidth -= gap;
        }
        contentWidth = Math.max(Math.min(minContentWidth, availableWidth), contentWidth);

        int contentX = padding + (sidebarOverlay ? 0 : sidebarWidth + gap);
        LayoutRect sidebarBounds = new LayoutRect(padding, panelTop, Math.min(sidebarWidth, availableWidth), panelHeight);
        LayoutRect contentBounds = new LayoutRect(contentX, panelTop, Math.min(contentWidth, availableWidth), panelHeight);

        int detailX = detailOverlay
                ? Math.max(padding, this.width - padding - detailWidth)
                : contentBounds.x + contentBounds.width + (detailVisible ? gap : 0);
        LayoutRect detailBounds = detailVisible
                ? new LayoutRect(detailX, panelTop, Math.min(detailWidth, availableWidth), panelHeight)
                : new LayoutRect(detailX, panelTop, 0, panelHeight);

        return new MainLayoutMetrics(
                responsiveTier,
                sidebarBounds,
                contentBounds,
                detailBounds,
                sidebarOverlay,
                detailOverlay,
                sidebarVisible,
                detailVisible,
                padding,
                gap
        );
    }

    /**
     * 閻犱緤绱曢悾鏄忋亹閹惧啿顤呮俊妤嬬导缂嶅懏绋夌€ｎ剚鐣卞鑸电墪閻増娼忕涵鍛崺闁?     *
     * @param config 鐟滅増鎸告晶鐘绘煀瀹ュ洨鏋傞悗鐢殿攰閽?
     * @return 缂備礁绻楃换鍐棘椤撶姴浠ǎ鍥跺枟椤掓粓鎯冮崟顔剧彾閻?     */
    private int resolveLayoutPadding(ModConfig config) {
        int basePadding = clampInt(config.getPadding(), 6, 20);
        return switch (responsiveTier) {
            case LARGE -> Math.max(10, basePadding);
            case MEDIUM -> Math.max(8, Math.min(basePadding, 12));
            case COMPACT -> Math.max(6, Math.min(basePadding, 10));
            case MINIMAL -> Math.max(4, Math.min(basePadding, 8));
        };
    }

    /**
     * 閻犱緤绱曢悾鏄忋亹閹惧啿顤呮俊妤嬬导缂嶅懏绋夌€ｎ剚鐣卞銈呮贡濞茬増绗熻閻栴喚鈧妫勭€规娊濡?     *
     * @param config 鐟滅増鎸告晶鐘绘煀瀹ュ洨鏋傞悗鐢殿攰閽?
     * @param availableWidth 闁告瑯鍨抽弫銈団偓纭呮鐎?
     * @return 闂侇偄鍊块崢銈夊触鎼达絾鐣卞〒姘€鍕焿閻庣妫勭€?
     */
    private int resolveSidebarWidth(ModConfig config, int availableWidth) {
        return switch (responsiveTier) {
            case LARGE -> clampInt(config.getProjectSidebarWidth(), 112, Math.min(156, Math.max(112, availableWidth / 3)));
            case MEDIUM -> clampInt(config.getProjectSidebarWidth(), 104, Math.min(136, Math.max(104, availableWidth / 3)));
            case COMPACT -> clampInt(config.getProjectSidebarWidth(), 96, Math.min(124, Math.max(96, availableWidth / 3)));
            case MINIMAL -> clampInt(Math.max(132, availableWidth - 84), 132, Math.max(132, Math.min(200, availableWidth - 8)));
        };
    }

    /**
     * 閻犱緤绱曢悾鏄忋亹閹惧啿顤呮俊妤嬬导缂嶅懏绋夌€ｎ剚鐣遍悹鍥烽檮閸庡繘宕犻崫鍕靛晬閹艰揪璐熼埀?     *
     * @param availableWidth 闁告瑯鍨抽弫銈団偓纭呮鐎?
     * @return 闂侇偄鍊块崢銈夊触鎼达絾鐣遍悹鍥烽檮閸庡繘宕犻崫鍕靛晬閹?     */
    private int resolveDetailWidth(int availableWidth) {
        return switch (responsiveTier) {
            case LARGE -> clampInt(150, 132, Math.max(132, Math.min(180, availableWidth / 2)));
            case MEDIUM -> clampInt(132, 120, Math.max(120, Math.min(156, availableWidth / 2)));
            case COMPACT -> clampInt(Math.max(148, availableWidth / 2), 148, Math.max(148, Math.min(180, availableWidth - 24)));
            case MINIMAL -> clampInt(Math.max(156, availableWidth - 72), 156, Math.max(156, availableWidth - 8));
        };
    }

    /**
     * 闁哄秷顫夊畵浣姐亹閹惧啿顤呴悽顖氬暙閻剝绠涢銈呭季闁哄洤鐡ㄩ弻濠勬啺閸℃瑦纾伴悘鐐插€歌ぐ鑼喆娴ｅ厜鍋撹閹蜂即鎯勭粙鍨綘闁硅矇鍌涱偨闁绘鍩栭埀顑块檷閳?     */
    private void applyResponsiveWidgetVisibility() {
        if (layoutMetrics == null) {
            return;
        }
        boolean sidebarVisible = layoutMetrics.sidebarVisible;
        boolean detailVisible = layoutMetrics.detailVisible;

        boolean teamSpaceVisible = sidebarVisible && currentSpaceMode == SpaceMode.TEAM;
        if (personalSpaceButton != null) {
            personalSpaceButton.visible = sidebarVisible;
            personalSpaceButton.active = sidebarVisible && currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.visible = sidebarVisible;
            teamSpaceButton.active = sidebarVisible && teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.visible = sidebarVisible;
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
            myViewButton.active = sidebarVisible && currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            unassignedViewButton.visible = teamSpaceVisible;
            unassignedViewButton.setMessage(Component.literal("\u5f85\u5206\u914d"));
            unassignedViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.visible = teamSpaceVisible;
            allViewButton.setMessage(Component.translatable("gui.todolist.all"));
            allViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.ALL;
        }
        if (projectSearchField != null) {
            projectSearchField.visible = sidebarVisible;
            projectSearchField.active = sidebarVisible;
        }
        if (addProjectBtn != null) {
            addProjectBtn.visible = sidebarVisible;
            addProjectBtn.active = sidebarVisible;
        }
        if (editProjectBtn != null) {
            editProjectBtn.visible = sidebarVisible;
            if (!sidebarVisible) {
                editProjectBtn.active = false;
            }
        }
        if (deleteProjectBtn != null) {
            if (!sidebarVisible) {
                deleteProjectBtn.visible = false;
                deleteProjectBtn.active = false;
            }
        }
        if (applyJoinProjectBtn != null) {
            if (!sidebarVisible) {
                applyJoinProjectBtn.visible = false;
                applyJoinProjectBtn.active = false;
            }
        }

        if (quickAddField != null) {
            quickAddField.visible = true;
            quickAddField.active = true;
        }
        applyDetailWidgetEditability();
        if (claimButton != null) {
            claimButton.visible = detailVisible && claimButton.visible;
            claimButton.active = detailVisible && claimButton.active;
        }
        if (abandonButton != null) {
            abandonButton.visible = detailVisible && abandonButton.visible;
            abandonButton.active = detailVisible && abandonButton.active;
        }
        if (assignOthersButton != null) {
            assignOthersButton.visible = detailVisible && assignOthersButton.visible;
            assignOthersButton.active = detailVisible && assignOthersButton.active;
        }
        if (sidebarToggleButton != null) {
            sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
            sidebarToggleButton.active = layoutMetrics.sidebarOverlay;
        }
    }

    /**
     * 闁告帒娲﹀畷鏌ュ几娴ｅ摜姣堢紒鎰殔瑜版稒绋夌€ｎ剚鐣卞銈呮贡濞茬増绗熻閻栴喚鎲伴崱娆愮０閻忕偛鍊堕埀?     */
    private void toggleSidebarOverlay() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            return;
        }
        sidebarOverlayVisible = !sidebarOverlayVisible;
        layoutMetrics = buildMainLayoutMetrics(ModConfig.getInstance());
        updateProjectActionButtons();
        updateButtonStates();
        applyResponsiveWidgetVisibility();
    }

    /**
     * 閺夆晜鏌ㄥú鏍ㄣ亜閸︻厽绐楀〒姘€鍕焿鐟滅増鎸告晶鐘诲及椤栨碍鍎婇柛娆樺灥椤棝濡?     *
     * @return true 閻炴稏鍔庨妵姘亜閸︻厽绐楀〒姘€鍕焿闁告瑯鍨甸～?
     */
    private boolean isSidebarPanelVisible() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 閺夆晜鏌ㄥú鏍嫚閿旇棄鍓伴柛鏍ф惈缂嶅宕滃鍡樞﹂柛姘剧畱瑜拌尙鎲存担纰樺亾?     *
     * @return true 閻炴稏鍔庨妵姘辨嫚閿旇棄鍓伴柛鏍ф惈瑜拌尙鎲?     */
    private boolean isDetailPanelVisible() {
        return layoutMetrics != null && layoutMetrics.detailVisible;
    }

    private void toggleTaskCompletion(Task task) {
        if (!canToggleCompletion(task)) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        taskManager.toggleTaskCompletion(task.getId());
        markUnsaved();
        refreshTaskList();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        Component title = hasUnsavedChanges ? Component.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 10, 0xFFFFFFFF, false);

        renderLayoutPanels(context);
        if (taskListWidget != null) taskListWidget.render(context, mouseX, mouseY, delta);
        if (projectListWidget != null && isSidebarPanelVisible()) {
            projectListWidget.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);

        int color = 0xFFFFFFFF;
        int textH = this.font.lineHeight;

        if (quickAddField != null && quickAddField.visible) {
            int plusY = quickAddField.getY() + (quickAddField.getHeight() - textH) / 2;
            context.drawString(this.font, "+", Math.max(4, quickAddField.getX() - 10), plusY, color, false);
        }
        if (descField != null && descField.visible) {
            int dy = descField.getY() - textH - 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.description"), descField.getX(), dy, color, false);
        }
        if (tagField != null && tagField.visible) {
            int zy = tagField.getY() - textH - 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.tags"), tagField.getX(), zy, color, false);
        }
        /*
        if (searchField != null) {
            int sy = searchField.getY() + (searchField.getHeight() - textH) / 2;
            int searchLabelX = searchField.getX() - 40;
            context.drawString(this.font, Component.translatable("gui.todolist.label.search"), searchLabelX, sy, color, false);
        }
        */
        renderTaskContextMenu(context, mouseX, mouseY);
        renderNotifications(context);
    }

    /**
     * 缂備焦锚閸╂绋夐懡銈嗘珪闂傚牄鍨诲▓鎴︽閵忊剝绶查柤鍐叉湰濞呮瑩鏁嶇仦钘夌盎闁告柡鏅為々顐︽儎閺嵮呯濞撴皜鍕焿闁告粌鐭侀娑㈠箚閸涱厼闅樺☉鎾崇凹鐎靛矂宕橀崨顓у晣闁告牕鎼崹搴ｄ沪閸屾稒鈻旂紒鈧幁鎺嗗亾?     *
     * @param context 鐟滅増鎸告晶鐘电磼濡搫鐓戝☉鎾筹梗缁楀懘寮?     */
    private void renderLayoutPanels(GuiGraphics context) {
        if (layoutMetrics == null) {
            return;
        }
        renderPanelBackground(context, layoutMetrics.contentBounds, false);
        if (isSidebarPanelVisible()) {
            renderPanelBackground(context, layoutMetrics.sidebarBounds, layoutMetrics.sidebarOverlay);
        }
        if (isDetailPanelVisible()) {
            renderPanelBackground(context, layoutMetrics.detailBounds, layoutMetrics.detailOverlay);
        }
    }

    /**
     * 缂備焦锚閸╂宕￠弴姘跺殝闂傚牄鍨哄姗€鎳楃仦鐐彲闁告粌鏈鎸庢綇楠炲簱鍋?     *
     * @param context 鐟滅増鎸告晶鐘电磼濡搫鐓戝☉鎾筹梗缁楀懘寮?     * @param bounds 闂傚牄鍨哄妯绘綇閸︻厽娅?
     * @param overlay 鐟滅増鎸告晶鐘绘閵忊剝绶查柡鍕靛灠閹焦绋夋ウ娆炬船闁烩晜鐗曠槐?
     */
    private void renderPanelBackground(GuiGraphics context, LayoutRect bounds, boolean overlay) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        int fillColor = overlay ? 0xD91A1A1A : 0x8C111111;
        int outlineColor = overlay ? 0xCCB8B8B8 : 0x66888888;
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, fillColor);
        context.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, outlineColor);
    }

    private void renderNotifications(GuiGraphics context) {
        if (notifications.isEmpty()) return;
        long now = System.currentTimeMillis();

        int boxWidth = 220;
        int boxHeight = 20;
        int startX = Math.max(8, this.width - boxWidth - 8);
        int startY = (searchField != null) ? searchField.getY() : 35;
        int gap = 4;

        List<Notification> active = new ArrayList<>();
        for (Notification n : notifications) {
            if (n.expireAt > now) active.add(n);
        }
        notifications.clear();
        notifications.addAll(active);

        int dy = 0;
        for (Notification n : notifications) {
            int bx1 = startX;
            int by1 = startY + dy;
            int bx2 = bx1 + boxWidth;
            int by2 = by1 + boxHeight;
            context.fill(bx1, by1, bx2, by2, 0xCC000000);
            context.renderOutline(bx1, by1, boxWidth, boxHeight, 0xFFFFFFFF);
            int tx = bx1 + 6;
            int ty = by1 + (boxHeight - this.font.lineHeight) / 2;
            context.drawString(this.font, Component.nullToEmpty(n.text), tx, ty, 0xFFFFFF00, false);
            dy += boxHeight + gap;
        }
    }

    private void onSaveTasks() {
        boolean personalSaved = !personalHasUnsavedChanges;
        boolean teamSaved = !teamHasUnsavedChanges;

        if (personalHasUnsavedChanges) {
            try {
                savePersonalTasks();
                personalSaved = true;
                personalHasUnsavedChanges = false;
            } catch (Exception e) {
                personalSaved = false;
                TodoConstants.LOGGER.error("Failed to save personal tasks", e);
            }
        }

        if (teamHasUnsavedChanges) {
            try {
                saveTeamTasks();
                teamSaved = true;
                teamHasUnsavedChanges = false;
            } catch (Exception e) {
                teamSaved = false;
                TodoConstants.LOGGER.error("Failed to save team tasks", e);
            }
        }

        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        if (personalSaved && teamSaved) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.todolist.saved"), false);
            }
            onClose();
            return;
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("message.todolist.save_failed"), false);
        }
    }

    /**
     * 濞ｅ洦绻傞悺銊ㄣ亹閹惧啿顤呴悗骞垮灪閸╂稓绮╅婊呭閻庢稒眉閼垫垿鎯冮崟顏堝殝濞存粏妗ㄩ幑銏ゅ礉閳藉懐绀夋鐐舵硾閹挸顫㈤妷銉ョ厒闁哄牆绉存慨鐔虹博椤栨瑧鐟?HUD闁?
     *
     * @throws Exception 鐟滅増鎸烽柌婊勭鏉炵増宕查柛鏂衡偓鑼閻庢稒锚閵囨垹鎷归妷锔筋槯闁硅埖绋戦崵顓烆嚕閸屾氨鍩?
     */
    private void savePersonalTasks() throws Exception {
        if (personalTaskManager == null) {
            return;
        }
        List<Task> personalTasks = personalTaskManager.getAllTasks();
        ClientTaskStorageHelper.savePersonalTasks(TodoListCommon.getTaskStorage(), this.minecraft, personalTasks);
        if (ClientBridge.ops() != null) {
            ClientBridge.ops().sendReplaceAllTasks(personalTasks);
        }
        TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
        if (renderer != null) {
            renderer.forceRefreshTasks();
        }
        TodoConstants.LOGGER.info("Personal tasks saved");
    }

    /**
     * 濞ｅ洦绻傞悺銊ㄣ亹閹惧啿顤呴悗骞垮灪閸╂稓绮╅婊呭閻庢稒眉閼垫垿鎯冮崟顐ｇ闂傚啰鍠嶉幑銏ゅ礉閳藉懐绀夋鐐舵硾閹挸顫㈤妷銉ョ厒闁哄牆绉存慨鐔虹博椤栨ǚ鍋?
     *
     * @throws Exception 鐟滅増鎸稿ú鐔兼⒓閻旇埖宕查柛鏂衡偓鑼閻庢稒锚閵囨垹鎷归妷锔筋槯闁硅埖绋戦崵顓烆嚕閸屾氨鍩?
     */
    private void saveTeamTasks() throws Exception {
        if (teamTaskManager == null) {
            return;
        }
        List<Task> teamTasks = teamTaskManager.getAllTasks();
        if (ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(this.minecraft)) {
            TodoListCommon.getTaskStorage().saveTeamTasks(teamTasks);
        }
        if (ClientBridge.ops() != null) {
            ClientBridge.ops().sendReplaceTeamTasks(teamTasks);
        }
        TodoConstants.LOGGER.info("Team tasks saved");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && hasContextMenu()) {
            closeTaskContextMenu();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (quickAddField != null && quickAddField.isFocused()) {
                if (!isAddTaskAllowedInCurrentView()) {
                    addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
                    return true;
                }
                if (selectedTask != null) {
                    return true;
                }
                onAddTask();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updatePrioritySelection() {
    }

    private boolean isClickInEditArea(double mouseX, double mouseY) {
        if (quickAddField != null && quickAddField.isMouseOver(mouseX, mouseY)) return true;
        if (titleField != null && titleField.visible && titleField.isMouseOver(mouseX, mouseY)) return true;
        if (descField != null && descField.visible && descField.isMouseOver(mouseX, mouseY)) return true;
        if (tagField != null && tagField.visible && tagField.isMouseOver(mouseX, mouseY)) return true;
        if (detailCloseButton != null && detailCloseButton.visible && detailCloseButton.isMouseOver(mouseX, mouseY)) return true;
        if (claimButton != null && claimButton.visible && claimButton.isMouseOver(mouseX, mouseY)) return true;
        if (abandonButton != null && abandonButton.visible && abandonButton.isMouseOver(mouseX, mouseY)) return true;
        if (assignOthersButton != null && assignOthersButton.visible && assignOthersButton.isMouseOver(mouseX, mouseY)) return true;
        if (isInsideContextMenu(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && layoutMetrics != null && layoutMetrics.sidebarOverlay && layoutMetrics.sidebarVisible
                && !layoutMetrics.sidebarBounds.contains(mouseX, mouseY)
                && (sidebarToggleButton == null || !sidebarToggleButton.isMouseOver(mouseX, mouseY))) {
            sidebarOverlayVisible = false;
            layoutMetrics = buildMainLayoutMetrics(ModConfig.getInstance());
            applyResponsiveWidgetVisibility();
        }
        if (taskListWidget != null && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            pendingClickSelectionTask = null;
            closeTaskContextMenu();
            return true;
        }
        if (projectListWidget != null && isSidebarPanelVisible() && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
            pendingClickSelectionTask = null;
            closeTaskContextMenu();
            return true;
        }

        if (taskListWidget != null) {
            TaskListWidget.TaskSectionHitResult sectionHit = taskListWidget.getSectionAt(mouseX, mouseY);
            if (button == 0
                    && sectionHit != null
                    && sectionHit.getRowType() == TaskListWidget.RowType.SECTION_HEADER
                    && "completed".equals(sectionHit.getSectionId())) {
                toggleCompletedSection();
                applySearchFilter();
                pendingClickSelectionTask = null;
                taskRowDragInProgress = false;
                taskRowDragOrderSnapshot = List.of();
                closeTaskContextMenu();
                return true;
            }
            Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
            if (clickedTask != null) {
                if (button == 0 && isTaskReorderAllowedInCurrentView() && taskListWidget.canStartDrag(clickedTask)) {
                    String dragSectionId = sectionHit == null ? "active" : sectionHit.getSectionId();
                    taskListWidget.armPendingTaskDrag(clickedTask, dragSectionId, mouseX, mouseY);
                    pendingClickSelectionTask = clickedTask;
                    taskRowDragInProgress = false;
                    taskRowDragOrderSnapshot = getCurrentVisibleActiveTaskIds();
                    closeTaskContextMenu();
                    return true;
                }
                pendingClickSelectionTask = null;
                taskRowDragInProgress = false;
                taskRowDragOrderSnapshot = List.of();
                selectTask(clickedTask);
                if (button == 1) {
                    openTaskContextMenu(clickedTask, (int) mouseX, (int) mouseY);
                } else {
                    closeTaskContextMenu();
                }
                return true;
            }
        }

        if (button == 0
                && titleField != null
                && titleField.visible
                && titleField.isMouseOver(mouseX, mouseY)
                && (detailDraft == null || !detailDraft.titleEditing)) {
            beginDetailTitleEditing();
            return true;
        }

        boolean cleared = false;
        pendingClickSelectionTask = null;
        taskRowDragInProgress = false;
        taskRowDragOrderSnapshot = List.of();
        if (button == 0 && selectedTask != null && !isClickInEditArea(mouseX, mouseY)) {
            clearSelectedTask();
            cleared = true;
        }
        if (button == 0 || button == 1) {
            closeTaskContextMenu();
        }

        if (this.minecraft == null || Minecraft.getInstance() == null) {
            return cleared;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        return handled || cleared;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean handled = false;
        if (taskListWidget != null) {
            handled = taskListWidget.mouseScrolled(mouseX, mouseY, 0, amount);
        }
        if (!handled && projectListWidget != null && isSidebarPanelVisible()) {
            handled = projectListWidget.mouseScrolled(mouseX, mouseY, amount);
        }
        if (!handled) {
            handled = super.mouseScrolled(mouseX, mouseY, amount);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        syncTaskListReorderState();
        if (taskListWidget != null && taskListWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            if (pendingClickSelectionTask != null || taskListWidget.getDropTargetIndexForTest() >= 0) {
                taskRowDragInProgress = true;
                markUnsaved();
            }
            pendingClickSelectionTask = null;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
        if (taskListWidget != null && taskListWidget.mouseReleased(mouseX, mouseY, button)) {
            if (taskRowDragInProgress || taskListWidget.getDropTargetIndexForTest() >= 0) {
                markUnsaved();
                List<Task> reorderedActiveTasks = taskListWidget.getTasks().stream()
                        .filter(Objects::nonNull)
                        .filter(task -> !task.isCompleted())
                        .toList();
                List<String> reorderedIds = reorderedActiveTasks.stream()
                        .map(Task::getId)
                        .filter(Objects::nonNull)
                        .toList();
                if (!reorderedIds.equals(taskRowDragOrderSnapshot)) {
                    onManualReorderActiveTasks(reorderedActiveTasks);
                }
            }
            pendingClickSelectionTask = null;
            taskRowDragInProgress = false;
            taskRowDragOrderSnapshot = List.of();
            return true;
        }
        if (button == 0 && pendingClickSelectionTask != null) {
            Task taskToSelect = pendingClickSelectionTask;
            pendingClickSelectionTask = null;
            taskRowDragInProgress = false;
            taskRowDragOrderSnapshot = List.of();
            selectTask(taskToSelect);
            return true;
        }
        pendingClickSelectionTask = null;
        taskRowDragInProgress = false;
        taskRowDragOrderSnapshot = List.of();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        discardPersonalTasksOnCloseIfNeeded();
        if (viewMode != ViewMode.PERSONAL && teamHasUnsavedChanges) {
            ClientBridge.ops().requestTeamSync();
            hasUnsavedChanges = false;
            teamHasUnsavedChanges = false;
        }
        this.minecraft.setScreen(parent);
    }

    /**
     * 闁革负鍔岄崣褔姊婚鐘虫珪闂傚牄鍨哄鍌涚▔閵忕姷纾鹃柡鍫簷缁绘氨鈧稒顭囧▓鎴炵▔椤忓啯鐪藉ù鐘侯嚙婵喖寮ㄩ悷鏉啃楅柨娑樺缁绘岸骞愭担娴嬪亾濠娾偓缁孩绌卞┑鍡欐憼闁圭顦甸幐鎶芥媰閻ｅ本纾搁柍銉︾箚椤曘垺绋婃径鍫氬亾?
     */
    private void discardPersonalTasksOnCloseIfNeeded() {
        if (!personalHasUnsavedChanges || personalTaskManager == null) {
            return;
        }
        try {
            List<Task> persistedTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), this.minecraft);
            personalTaskManager.clearAll();
            for (Task task : persistedTasks) {
                personalTaskManager.addTask(task);
            }
            personalHasUnsavedChanges = false;
            if (viewMode == ViewMode.PERSONAL) {
                hasUnsavedChanges = false;
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to discard unsaved personal tasks on close", e);
        }
    }

    private void onCancel() {
        onClose();
    }

    // Event handlers

    private void onAddTask() {
        if (currentProject == null) {
            addNotification(Component.translatable("message.todolist.select_project_first").getString());
            return;
        }
        if (!isAddTaskAllowedInCurrentView()) {
            addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
            return;
        }
        if (viewMode != ViewMode.PERSONAL) {
            Role role = getCurrentRole();
            ViewScope scope = getCurrentViewScope();
            boolean projectMember = isCurrentPlayerProjectMember();
            boolean allowMemberCreate = currentProject.isAllowMemberCreate();
            if (!PermissionCenter.canPerform(Operation.ADD_TASK, role, new Context(scope, false, false, false, false, false, projectMember, allowMemberCreate))) {
                addNotification(Component.translatable("message.todolist.no_permission_add_team").getString());
                return;
            }
        }
        String title = getFieldValue(quickAddField, "");
        String desc = "";

        if (!title.isEmpty()) {
            Task task = taskManager.addTask(title, desc);
            task.setPriority(selectedPriority);
            if (currentProject != null) {
                task.setProjectId(currentProject.getId());
            }

            if (viewMode != ViewMode.PERSONAL) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    String uuid = this.minecraft.player.getUUID().toString();
                    String name = this.minecraft.player.getName().getString();
                    task.setScope(Task.Scope.TEAM);
                    task.setCreatorUuid(uuid);
                    if (viewMode == ViewMode.TEAM_ASSIGNED) {
                        task.setAssigneeUuid(uuid);
                        task.setAssigneeName(name);
                    }
                } else {
                    task.setScope(Task.Scope.TEAM);
                }
            }

            clearSelectedTask();
            selectedPriority = Task.Priority.MEDIUM;
            if (quickAddField != null) {
                quickAddField.setValue("");
            }

            markUnsaved();
            refreshTaskList();
        }
    }

    private void onDeleteTask() {
        if (selectedTask != null) {
            String id = selectedTask.getId();
            taskManager.deleteTask(id);
            clearSelectedTask();
            closeTaskContextMenu();
            markUnsaved();
            refreshTaskList();
        }
    }

    private void selectTask(Task task) {
        selectedTask = task;
        selectedPriority = task.getPriority();
        detailDraft = createDetailDraft(task);
        detailOverlayVisible = true;
        rebuildUI();
    }

    private void clearSelectedTask() {
        selectedTask = null;
        detailDraft = null;
        detailOverlayVisible = false;
        rebuildUI();
    }

    /**
     * 闁糕晞妗ㄧ花顒冦亹閹惧啿顤呴梺顐㈩槷閼垫垶绂掔拠鎻掝潳闁告帗绋戠紓鎾寸▔閳ь剚绂掗崐鐕佸殜闁诡垰鎳忔繛濠勪沪婢跺骸纾哥紒瀣骏閳?     *
     * @param task 鐟滅増鎸告晶鐘绘焻婢跺鍘ù鐘侯嚙婵?
     * @return 閻庣數鎳撶花鏌ユ儍閸曨噮鍤婇柟顖氭嚀瀹曞繒绮欓崠锛勫耿鐟滅増鎸烽幑銏ゅ礉閳ヨ尪绀嬬紒宀€鍎ゅ鍌涙交閺傛寧绀€ null
     */
    private TaskDetailDraft createDetailDraft(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskDetailDraft(task.getId(), task.getTitle(), task.getDescription(), joinTaskTags(task));
    }

    /**
     * 閻忓繐妫楃紞瀣礈瀹ュ牜鍤婇柟顖氭嚀瀹曞繒绮欓崹顔藉€辨慨婵勫劚閸╁矂鎮惧畝鍕〃闁硅矇鍌涱偨闁挎稑鐭傛导鈺呭礂瀹ュ鍋撴径瀣仴闁告帒娲﹀畷鏌ュ籍閼哥數鏆欓柣锝嗙懄濡偊宕橀崨顓у晣闁?     */
    private void syncDetailWidgetsFromState() {
        syncingDetailWidgets = true;
        try {
            if (titleField != null) {
                titleField.setValue(detailDraft == null ? "" : detailDraft.title);
            }
            if (descField != null) {
                descField.setValue(detailDraft == null ? "" : detailDraft.description);
            }
            if (tagField != null) {
                tagField.setValue(detailDraft == null ? "" : detailDraft.tags);
            }
        } finally {
            syncingDetailWidgets = false;
        }
        applyDetailWidgetEditability();
    }

    /**
     * 闁圭顦紞瀣礈瀹ュ棗鈻曢悘鐐差槺婵悂骞€娴ｈ绾柡鍌滃閻栵絾锛愬Ο绯曞亾娴ｇ懓浼庨弶鈺傛緲閹蜂即寮介崶鈺婂姰闁汇劌瀚ぐ鑼磽閺嶎剛甯嗛柟顑讲鍋?     */
    private void applyDetailWidgetEditability() {
        boolean detailVisible = layoutMetrics == null ? selectedTask != null : layoutMetrics.detailVisible;
        boolean editable = detailVisible && canEditSelectedTaskDetails();
        if (titleField != null) {
            boolean titleEditing = editable && detailDraft != null && detailDraft.titleEditing;
            titleField.setEditable(titleEditing);
            titleField.active = detailVisible;
            titleField.visible = detailVisible;
        }
        if (descField != null) {
            descField.active = editable;
            descField.visible = detailVisible;
        }
        if (tagField != null) {
            tagField.setEditable(editable);
            tagField.visible = detailVisible;
        }
        if (detailCloseButton != null) {
            boolean showCloseButton = detailVisible && selectedTask != null;
            detailCloseButton.visible = showCloseButton;
            detailCloseButton.active = showCloseButton;
        }
    }

    /**
     * 闁告帇鍊栭弻鍥亹閹惧啿顤呴梺顐㈩槷閼垫垶绂掔拠鎻掝潳闁哄嫷鍨伴幆渚€宕楁担绛嬪晠闁革负鍔忛娑㈠箚閸涱喖鈻曢悘鐐差槷閼垫垹绱撻弽顒傚竼闁糕晞娅ｉ、鍛偓娑欘殕椤斿矂濡?     *
     * @return true 閻炴稏鍔庨妵姘炽亹閹惧啿顤呭ù鐘侯嚙婵喖宕ｉ婊呮そ閺?     */
    private boolean canEditSelectedTaskDetails() {
        return selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted() && canEditTask(selectedTask);
    }

    /**
     * 閻犱讲鏅為娑㈠箚閸涱喚鍨煎Λ鐗堫焾缁绘﹢宕楅妷褏妞介弶鍫熷灦閳ь兛绶ょ槐婵囩瑹濞戞艾浠柛鎴犵帛閻栵絾锛愬Ο璇茬仐婵炴潙顑堥惁顖涖仚閸楃偛袟濠㈣泛绉堕弫銈夊Υ?     */
    private void beginDetailTitleEditing() {
        if (detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.titleEditing = true;
        applyDetailWidgetEditability();
        if (titleField != null) {
            titleField.setFocused(true);
            this.setFocused(titleField);
        }
    }

    /**
     * 濠㈣泛瀚幃濠勬嫚閿旇棄鍓伴柡宥呮喘椤ｄ粙宕ｅΟ缁樼函闁挎稑鑻懟鐔煎触鐏炵虎鍔勯柛銉у仜缂嶅宕滃澶嗗亾婢跺鍘ù鐘侯嚙婵喖濡?     *
     * @param text 闁哄牃鍋撻柡鍌滃閻栵絾锛愬Ο缁樼€柡?     */
    private void onDetailTitleChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.title = text == null ? "" : text;
        selectedTask.setTitle(detailDraft.title);
        markUnsaved();
    }

    /**
     * 濠㈣泛瀚幃濠勬嫚閿旇棄鍓伴柟璇茬箺閸亪宕ｅΟ缁樼函闁挎稑鑻懟鐔煎触鐏炵虎鍔勯柛銉у仜缂嶅宕滃澶嗗亾婢跺鍘ù鐘侯嚙婵喖濡?     *
     * @param text 闁哄牃鍋撻柡鍌滃瀵寧娼婚悧鍫熺€柡?     */
    private void onDetailDescriptionChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.description = text == null ? "" : text;
        selectedTask.setDescription(detailDraft.description);
        markUnsaved();
    }

    /**
     * 濠㈣泛瀚幃濠勬嫚閿旇棄鍓伴柡宥呮川椤掔兘宕ｅΟ缁樼函闁挎稑鑻懟鐔煎触鐏炵虎鍔勯柛銉у仜缂嶅宕滃澶嗗亾婢跺鍘ù鐘侯嚙婵喖濡?     *
     * @param text 闁哄牃鍋撻柡鍌滃閻栵絿绮甸悙顒佺€柡?     */
    private void onDetailTagsChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.tags = text == null ? "" : text;
        String value = getFieldValue(tagField, "");
        if (value.isEmpty()) {
            selectedTask.clearTags();
        } else {
            List<String> tags = new ArrayList<>();
            for (String part : value.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            selectedTask.setTags(tags);
        }
        markUnsaved();
    }

    /**
     * 閻忓繐妫旈幑銏ゅ礉閳╁啰鍨肩紒娑氬亾鐎氶箖骞掗妷锕€鐏囬悹鍥烽檮閸庡繘骞庨挊澶屾簞濞达綀娉曢弫銈夋儍閸曨垪鍋撳Δ鈧ぐ鍧楀礆閸℃稒顓鹃柡鍌氭处濠€浼村Υ?     *
     * @param task 闁烩晩鍠楅悥锝嗙鐠囨彃顫?
     * @return 闂侇偅顨呰ぐ鍧楀礆閸℃稒顓鹃柛姘捣濞堟垿寮介崶鈺婂姰闁哄倸娲﹀﹢?
     */
    private String joinTaskTags(Task task) {
        if (task == null || task.getTags() == null || task.getTags().isEmpty()) {
            return "";
        }
        return String.join(",", task.getTags());
    }

    private boolean isSelectedTaskValid() {
        if (selectedTask == null) {
            return false;
        }
        if (currentProject == null) {
            return false;
        }
        String selectedId = selectedTask.getId();
        if (selectedId == null || selectedId.isEmpty()) {
            return false;
        }
        String projectId = currentProject.getId();
        if (!selectedTask.belongsToProject(projectId)) {
            return false;
        }
        for (TaskListWidget.SectionModel section : buildTaskPaneSections()) {
            if (section == null) {
                continue;
            }
            for (Task task : section.getTasks()) {
                if (task != null && selectedId.equals(task.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAddTaskAllowedInCurrentView() {
        return viewMode == ViewMode.PERSONAL || viewMode == ViewMode.TEAM_UNASSIGNED;
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedTask != null;
        boolean isCompleted = hasSelection && selectedTask.isCompleted();
        boolean isAssigned = hasSelection
                && selectedTask.getAssigneeUuid() != null
                && !selectedTask.getAssigneeUuid().isEmpty();
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isAssigneeSelf = hasSelection && isCurrentPlayerAssignee(selectedTask);
        boolean projectMember = isCurrentPlayerProjectMember();
        boolean allowMemberCreate = currentProject != null && currentProject.isAllowMemberCreate();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember, allowMemberCreate);
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        boolean detailVisible = layoutMetrics == null ? hasSelection : layoutMetrics.detailVisible;
        if (claimButton != null) {
            claimButton.visible = detailVisible && showAssignButtons;
            boolean canClaim = hasSelection
                    && PermissionCenter.canPerform(Operation.CLAIM_TASK, role, context);
            claimButton.active = detailVisible && showAssignButtons && canClaim;
        }
        if (abandonButton != null) {
            abandonButton.visible = detailVisible && showAssignButtons;
            boolean canAbandon = hasSelection
                    && PermissionCenter.canPerform(Operation.ABANDON_TASK, role, context);
            abandonButton.active = detailVisible && showAssignButtons && canAbandon;
        }
        if (assignOthersButton != null) {
            boolean showAssignOthers = showAssignButtons
                    && PermissionCenter.canPerform(Operation.ASSIGN_OTHERS, role, context);
            assignOthersButton.visible = detailVisible && showAssignOthers;
            boolean canAssignOthers = detailVisible && showAssignOthers && hasSelection;
            assignOthersButton.active = canAssignOthers;
        }
        rebuildContextMenuIfNeeded();
        applyResponsiveWidgetVisibility();
    }

    private void setSelectedPriority(Task.Priority priority) {
        this.selectedPriority = priority;
    }

    private boolean hasContextMenu() {
        return contextMenuTask != null && !contextMenuItems.isEmpty();
    }

    private void openTaskContextMenu(Task task, int mouseX, int mouseY) {
        if (task == null) {
            closeTaskContextMenu();
            return;
        }
        contextMenuTask = task;
        contextMenuItems = buildContextMenuItems(task);
        if (contextMenuItems.isEmpty()) {
            closeTaskContextMenu();
            return;
        }
        int maxTextWidth = 0;
        for (ContextMenuItem item : contextMenuItems) {
            maxTextWidth = Math.max(maxTextWidth, this.font.width(item.text));
        }
        contextMenuWidth = Math.max(90, maxTextWidth + 16);
        int menuHeight = contextMenuItems.size() * contextMenuItemHeight;
        contextMenuX = Math.max(4, Math.min(mouseX, this.width - contextMenuWidth - 4));
        contextMenuY = Math.max(4, Math.min(mouseY, this.height - menuHeight - 4));
    }

    private List<ContextMenuItem> buildContextMenuItems(Task task) {
        List<ContextMenuItem> items = new ArrayList<>();
        if (task == null) {
            return items;
        }
        boolean canEdit = canEditTask(task) && !task.isCompleted();
        boolean canDelete = canDeleteTask(task);
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.high"), canEdit, () -> applyTaskPriority(task, Task.Priority.HIGH)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.medium"), canEdit, () -> applyTaskPriority(task, Task.Priority.MEDIUM)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.low"), canEdit, () -> applyTaskPriority(task, Task.Priority.LOW)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.delete"), canDelete, () -> deleteTaskFromContextMenu(task)));
        return items;
    }

    private void applyTaskPriority(Task task, Task.Priority priority) {
        if (task == null || priority == null || !canEditTask(task) || task.isCompleted()) {
            closeTaskContextMenu();
            return;
        }
        task.setPriority(priority);
        setSelectedPriority(priority);
        markUnsaved();
        refreshTaskList();
        if (taskListWidget != null) {
            taskListWidget.ensureVisible(task);
        }
        ClientBridge.ops().sendUpdateTask(task);
        closeTaskContextMenu();
    }

    private void deleteTaskFromContextMenu(Task task) {
        if (task == null || !canDeleteTask(task)) {
            closeTaskContextMenu();
            return;
        }
        taskManager.deleteTask(task.getId());
        if (selectedTask != null && selectedTask.getId() != null && selectedTask.getId().equals(task.getId())) {
            clearSelectedTask();
        }
        markUnsaved();
        refreshTaskList();
        closeTaskContextMenu();
    }

    private void renderTaskContextMenu(GuiGraphics context, int mouseX, int mouseY) {
        if (!hasContextMenu()) {
            return;
        }
        int menuHeight = contextMenuItems.size() * contextMenuItemHeight;
        context.fill(contextMenuX, contextMenuY, contextMenuX + contextMenuWidth, contextMenuY + menuHeight, 0xEE111111);
        context.renderOutline(contextMenuX, contextMenuY, contextMenuWidth, menuHeight, 0xFFFFFFFF);
        for (int i = 0; i < contextMenuItems.size(); i++) {
            ContextMenuItem item = contextMenuItems.get(i);
            int itemTop = contextMenuY + i * contextMenuItemHeight;
            int itemBottom = itemTop + contextMenuItemHeight;
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                    && mouseY >= itemTop && mouseY < itemBottom;
            if (hovered) {
                context.fill(contextMenuX + 1, itemTop + 1, contextMenuX + contextMenuWidth - 1, itemBottom - 1, 0xFF2A2A2A);
            }
            int textColor = item.enabled ? 0xFFFFFFFF : 0xFF777777;
            int textY = itemTop + (contextMenuItemHeight - this.font.lineHeight) / 2;
            context.drawString(this.font, item.text, contextMenuX + 6, textY, textColor, false);
        }
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (!hasContextMenu()) {
            return false;
        }
        if (button != 0 && button != 1) {
            return false;
        }
        if (!isInsideContextMenu(mouseX, mouseY)) {
            if (button == 0 || button == 1) {
                closeTaskContextMenu();
            }
            return false;
        }
        if (button != 0) {
            return true;
        }
        int index = ((int) mouseY - contextMenuY) / contextMenuItemHeight;
        if (index < 0 || index >= contextMenuItems.size()) {
            closeTaskContextMenu();
            return true;
        }
        ContextMenuItem item = contextMenuItems.get(index);
        if (item.enabled && item.action != null) {
            item.action.run();
        } else {
            closeTaskContextMenu();
        }
        return true;
    }

    private boolean isInsideContextMenu(double mouseX, double mouseY) {
        if (!hasContextMenu()) {
            return false;
        }
        int menuHeight = contextMenuItems.size() * contextMenuItemHeight;
        return mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                && mouseY >= contextMenuY && mouseY < contextMenuY + menuHeight;
    }

    private void closeTaskContextMenu() {
        contextMenuTask = null;
        contextMenuItems = new ArrayList<>();
    }

    private void rebuildContextMenuIfNeeded() {
        if (!hasContextMenu()) {
            return;
        }
        Task menuTask = contextMenuTask;
        if (menuTask == null || filteredTasks == null) {
            closeTaskContextMenu();
            return;
        }
        for (Task task : filteredTasks) {
            if (task != null && task.getId() != null && task.getId().equals(menuTask.getId())) {
                contextMenuItems = buildContextMenuItems(task);
                contextMenuTask = task;
                if (contextMenuItems.isEmpty()) {
                    closeTaskContextMenu();
                }
                return;
            }
        }
        closeTaskContextMenu();
    }

    private boolean isAdminClient() {
        return this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
    }

    private void markUnsaved() {
        hasUnsavedChanges = true;
        if (viewMode == ViewMode.PERSONAL) {
            personalHasUnsavedChanges = true;
        } else {
            teamHasUnsavedChanges = true;
        }
    }

    /**
     * 濠㈣泛瀚幃濠呫亹閹惧啿顤呴柛娆樺灥椤棝寮甸鍕殮闁瑰瓨鍔掗幑銏ゅ礉閿涘嫭鐣遍柟闈涱儏婵晠鏌屽鍡楃瑩缂備焦鎸婚悘澶愭晬鐏炲€熷珯闁告艾鏈鐐哄嫉椤忓啰绠介悗娑欘焽婵悂骞€娴ｉ鐟㈤柛鎺擃殙閵嗗啴宕氶柨瀣厐闁?     *
     * @param reorderedActiveTasks 鐟滅増鎸告晶鐘诲矗椤栨繍娼岄柡鍫簻閻ｎ剟骞嬮幇顏呭床闁告棑绱曞▓鎴﹀棘娴兼番鈧孩鎯?     */
    private void onManualReorderActiveTasks(List<Task> reorderedActiveTasks) {
        if (!isTaskReorderAllowedInCurrentView() || taskManager == null || reorderedActiveTasks == null || reorderedActiveTasks.size() < 2) {
            return;
        }
        List<String> orderedTaskIds = reorderedActiveTasks.stream()
                .filter(Objects::nonNull)
                .map(Task::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orderedTaskIds.size() < 2) {
            return;
        }
        if (!taskManager.reorderTasks(orderedTaskIds)) {
            return;
        }
        if (selectedTask != null && selectedTask.getId() != null) {
            Task refreshedSelectedTask = taskManager.getTask(selectedTask.getId());
            if (refreshedSelectedTask != null) {
                selectedTask = refreshedSelectedTask;
            }
        }
        markUnsaved();
        refreshTaskList();
    }

    /**
     * 在鼠标交互前同步任务列表的拖拽排序开关，避免界面状态变更后列表仍保留旧权限快照。
     */
    private void syncTaskListReorderState() {
        if (taskListWidget == null) {
            return;
        }
        taskListWidget.setTaskReorderEnabled(isTaskReorderAllowedInCurrentView());
    }

    /**
     * 闁告帇鍊栭弻鍥亹閹惧啿顤呴悷娆忔濞存﹢寮伴姘剨闁稿繋娴囬蹇涘箥瑜戦、鎴﹀嫉椤忓嫮鏆氶柟瀛樺姃閹广垽宕濋敍鍕暠闁归潧顑呮慨鈺呭箳閹烘垹纰嶉柕?     *
     * @return true 閻炴稏鍔庨妵姘炽亹閹惧啿顤呴悷娆忔濞存﹢宕楁担绛嬪晠闁归攱鐗楃€氬潡骞掗幒鎴犵
     */
    private boolean isTaskReorderAllowedInCurrentView() {
        if (currentProject == null) {
            return false;
        }
        if (viewMode == ViewMode.PERSONAL) {
            return true;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean projectMember = isCurrentPlayerProjectMember();
        boolean allowMemberCreate = currentProject.isAllowMemberCreate();
        return PermissionCenter.canPerform(Operation.EDIT_TASK, role,
                new Context(scope, false, false, false, false, false, projectMember, allowMemberCreate));
    }

    public static boolean hasPersonalUnsavedChanges() {
        return personalHasUnsavedChanges;
    }

    private boolean canEditTask(Task task) {
        if (task == null) {
            return false;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean isAssigneeSelf = isCurrentPlayerAssignee(task);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.EDIT_TASK, role, context);
    }

    private boolean canDeleteTask(Task task) {
        if (task == null) {
            return false;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean isAssigneeSelf = isCurrentPlayerAssignee(task);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.DELETE_TASK, role, context);
    }

    private boolean canToggleCompletion(Task task) {
        if (task == null) {
            return false;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean isAssigneeSelf = isCurrentPlayerAssignee(task);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.TOGGLE_COMPLETE, role, context);
    }

    private boolean isCurrentPlayerAssignee(Task task) {
        if (task == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        String assignee = task.getAssigneeUuid();
        return assignee != null && assignee.equals(uuid);
    }

    private Role getCurrentRole() {
        if (isAdminClient()) {
            return Role.OP;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return Role.MEMBER;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return Role.MEMBER;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        if (uuid.equals(currentProject.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole projectRole = currentProject.getMemberRole(uuid);
        if (projectRole == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    private boolean isCurrentPlayerProjectMember() {
        if (isAdminClient()) {
            return true;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return true;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        if (uuid.equals(currentProject.getOwnerUuid())) {
            return true;
        }
        return currentProject.getMemberRole(uuid) != null;
    }

    private ViewScope getCurrentViewScope() {
        if (viewMode == ViewMode.PERSONAL) {
            return ViewScope.PERSONAL;
        }
        if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            return ViewScope.TEAM_UNASSIGNED;
        }
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            return ViewScope.TEAM_ASSIGNED;
        }
        return ViewScope.TEAM_ALL;
    }

    private void onClaimTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        String assignee = selectedTask.getAssigneeUuid();
        if (assignee != null && !assignee.isEmpty() && !assignee.equals(uuid)) {
            addNotification(Component.translatable("message.todolist.already_assigned").getString());
            return;
        }
        selectedTask.setAssigneeUuid(uuid);
        selectedTask.setAssigneeName(this.minecraft.player.getName().getString());
        addNotification(Component.translatable("message.todolist.assigned_to_me").getString());
        markUnsaved();
        refreshTaskList();
    }

    // Helper for rendering labels
    private class TextLabelWidget extends net.minecraft.client.gui.components.AbstractWidget {
        private final Component text;
        private final int color;
        
        public TextLabelWidget(int x, int y, Component text, int color) {
            super(x, y, minecraft.font.width(text), minecraft.font.lineHeight, text);
            this.text = text;
            this.color = color;
            this.active = false; // Not clickable
        }

        @Override
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            context.drawString(minecraft.font, text, getX(), getY(), color, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        }
    }
    
    private void updateProjectList() {
        if (projectListWidget == null) return;
        projectListWidget.setProjects(buildVisibleProjectsForSidebar());
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();
    }

    /**
     * 闁圭顦紞瀣礈瀹ュ洠鏁勯梻鍌氼嚟閻☆偊鏌呮径鍫氬亾娴ｈ鍋濈紒渚垮灪濞碱垱绂掔捄鐑樺濮掓稒顭堥缁樸亜閸︻厽绐楅柛蹇旂矊缁ㄥ磭鎲撮崟顐㈢仧闁挎稑鏈悗顖氼嚈鏉炵増娅犻柡宥呯箰瑜拌尙鎲存笟鈧妴宥夋儎椤旂厧鐏欓悶娑栧妸閳?     *
     * @return 鐟滅増鎸告晶鐘崇瑹瑜庨悥顔芥償閺冣偓濡绮堥搹瑙勭暠濡炪倕婀卞ú浼村礆濡ゅ嫨鈧?
     */
    private List<Project> buildVisibleProjectsForSidebar() {
        List<Project> all = new ArrayList<>();
        all.addAll(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
        all.addAll(projectManager.getProjectsByScope(Project.Scope.TEAM));

        Project defaultProject = null;
        for (Project project : all) {
            if (project == null || project.getScope() != projectScopeFilter) {
                continue;
            }
            if (projectScopeFilter == Project.Scope.PERSONAL && project.isDefaultPersonalProject()) {
                defaultProject = project;
                break;
            }
            if (projectScopeFilter == Project.Scope.TEAM && project.isDefaultTeamProject()) {
                defaultProject = project;
                break;
            }
        }

        List<Project> filtered = new ArrayList<>();
        String query = projectSearchQuery == null ? "" : projectSearchQuery.toLowerCase().trim();
        for (Project project : all) {
            if (project == null || project.getScope() != projectScopeFilter) {
                continue;
            }
            String searchableName = ProjectNameFormatter.toDisplayText(project).getString().toLowerCase();
            if (!query.isEmpty() && !searchableName.contains(query)) {
                continue;
            }
            filtered.add(project);
        }

        if (defaultProject != null && !containsProject(filtered, defaultProject.getId())) {
            filtered.add(0, defaultProject);
        }
        return filtered;
    }

    /**
     * 闁告帇鍊栭弻鍥儎椤旂晫鍨煎銈呮贡濞蹭即寮伴姘剨鐎规瓕灏欑划锛勨偓娑櫭﹢顏呯鎼粹檧鍋撳▎鎾亾婢跺﹤鐏欓悶娑栧妺閼垫垿濡?     *
     * @param projects 闁稿﹥鐟╅埀顒€顦甸妴宥夋儎椤旂厧鐏欓悶?     * @param projectId 闁烩晩鍠楅悥锝嗐亜閸︻厽绐楅柡宥呮穿閻?
     * @return true 閻炴稏鍔庨妵姘跺磹濞嗘挴鍋撴径濠傜仚閻炴稏鍔岄崙锟犲礌閸涱厽鍎撻柣鈺婂枟閻栵絾銇勯崷顓熺獥
     */
    private boolean containsProject(List<Project> projects, String projectId) {
        if (projects == null || projectId == null || projectId.isEmpty()) {
            return false;
        }
        for (Project project : projects) {
            if (project != null && projectId.equals(project.getId())) {
                return true;
            }
        }
        return false;
    }

    private void updateProjectActionButtons() {
        if (editProjectBtn == null || deleteProjectBtn == null || applyJoinProjectBtn == null) {
            return;
        }
        if (currentProject == null) {
            editProjectBtn.active = false;
            editProjectBtn.setMessage(Component.translatable("gui.todolist.edit"));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = false;
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            syncBottomProjectButtonsState();
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            editProjectBtn.active = true;
            editProjectBtn.setMessage(Component.translatable("gui.todolist.edit"));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = canDeleteCurrentProject();
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            syncBottomProjectButtonsState();
            return;
        }
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        boolean canEdit = PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx);
        editProjectBtn.active = true;
        editProjectBtn.setMessage(Component.translatable(canEdit ? "gui.todolist.edit" : "gui.todolist.project.view"));
        boolean member = isCurrentPlayerProjectMember();
        if (!member) {
            deleteProjectBtn.visible = false;
            deleteProjectBtn.active = false;
            applyJoinProjectBtn.visible = true;
            applyJoinProjectBtn.active = true;
            syncBottomProjectButtonsState();
            return;
        }
        applyJoinProjectBtn.visible = false;
        applyJoinProjectBtn.active = false;
        deleteProjectBtn.visible = true;
        deleteProjectBtn.active = canDeleteCurrentProject();
        syncBottomProjectButtonsState();
    }

    /**
     * 缂備胶鍠嶇粩鎾箰婢跺﹦绉奸柛鎾崇С閺呭爼寮借箛鎾宠閻熸瑤鐒﹂埀顑啫鐓曢柡鍌涙緲缁ㄦ娊鏌堥妸鈹库偓宥夋儎椤旇姤鎯欏ù锝嗙矊鐏忣垶鏁嶅畝鍕級闁稿繐绉电划鎾礉閵娿儱鐏欓悶娑栧妼婵傛牠宕鍡楃樆闂佺瓔鍠楀Ο澶愭⒕閹扳斁鍋?     */
    private void syncBottomProjectButtonsState() {
        boolean sidebarVisible = layoutMetrics == null || layoutMetrics.sidebarVisible;
        if (addProjectBtn != null) {
            addProjectBtn.visible = sidebarVisible;
            addProjectBtn.active = sidebarVisible;
        }
        if (editProjectBtn != null) {
            editProjectBtn.visible = sidebarVisible;
            editProjectBtn.active = sidebarVisible && editProjectBtn.active;
        }
        if (deleteProjectBtn != null) {
            deleteProjectBtn.visible = sidebarVisible && deleteProjectBtn.visible;
            deleteProjectBtn.active = sidebarVisible && deleteProjectBtn.active;
        }
        if (applyJoinProjectBtn != null) {
            applyJoinProjectBtn.visible = sidebarVisible && applyJoinProjectBtn.visible;
            applyJoinProjectBtn.active = sidebarVisible && applyJoinProjectBtn.active;
        }
    }
    
    private Component getProjectScopeText() {
        if (projectScopeFilter == Project.Scope.PERSONAL) {
            return Component.translatable("gui.todolist.project.toggle.personal");
        } else {
            return Component.translatable("gui.todolist.project.toggle.team");
        }
    }
    private void onAbandonTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean completed = selectedTask.isCompleted();
        String assignee = selectedTask.getAssigneeUuid();
        boolean assigned = assignee != null && !assignee.isEmpty();
        boolean assigneeSelf = isCurrentPlayerAssignee(selectedTask);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context ctx = new Context(scope, completed, assigned, assigneeSelf, false, false, projectMember);
        if (!PermissionCenter.canPerform(Operation.ABANDON_TASK, role, ctx)) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        selectedTask.setAssigneeUuid(null);
        selectedTask.setAssigneeName(null);
        addNotification(Component.translatable("message.todolist.abandoned_task").getString());
        markUnsaved();
        refreshTaskList();
    }

    private Component getPriorityFilterText() {
        // String labelKey = "gui.todolist.label.priority";
        String valueKey;
        switch (currentPriorityFilter) {
            case 1: 
                valueKey = "gui.todolist.filter.priority_high"; 
                break;
            case 2: 
                valueKey = "gui.todolist.filter.priority_medium"; 
                break;
            case 3: 
                valueKey = "gui.todolist.filter.priority_low"; 
                break;
            default: 
                valueKey = "gui.todolist.all"; 
                break;
        }
        return Component.translatable(valueKey);
    }

    private Component getStatusFilterText() {
        // MutableComponent label = Component.translatable("gui.todolist.label.status");
        Component value = "completed".equals(currentFilter) ? Component.translatable("gui.todolist.completed") : Component.translatable("gui.todolist.active");
        return value;
    }

    private Component getViewToggleText() {
        // MutableComponent label = Component.translatable("gui.todolist.label.view");
        String key;
        if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            key = "gui.todolist.view.team_unassigned";
        } else if (viewMode == ViewMode.TEAM_ALL) {
            key = "gui.todolist.view.team_all";
        } else if (viewMode == ViewMode.TEAM_ASSIGNED) {
            key = "gui.todolist.view.team_assigned";
        } else {
            key = "gui.todolist.view.personal";
        }
        return Component.translatable(key);
    }

    private void applyPriorityFilter() {
        filterTasks("active");
    }

    private void onAssignOthers() {
        if (selectedTask == null || this.minecraft == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        if (!canEditTask(selectedTask)) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        this.minecraft.setScreen(new AssignPlayerScreen(this, selectedTask));
    }

    /**
     * 閻熸瑱绲鹃悗浠嬪炊閵忋倖袝濡炪倕婀卞ú浼村箣閹邦剚鍠呴柛锔哄妼缂嶅宕滃鍜佸悅闁规挳顥撻顒佺▔婵犲嫭鐣遍柡鍕⒔閵囨岸宕ュ鍥嗙偤鏁嶇仦鑲╁枠闁稿繐鐗呮繛鍥偨閵娧呭閻庢稒锚閹洜绮旂敮顔剧妤犵偠娉涘﹢顏堝箣閹邦剚鍠呴柛锔哄妿閸ゅ酣寮捄鍝勭厱闁哄倿顣︾拹鐔煎嫉閳ь剟寮幍顔艰礋閻庣娉涢幃鏇㈠Υ?
     *
     * @param project 鐟滅増鎸告晶鐘诲炊閵忋倖袝濡炪倕婀卞ú?
     * @param memberUuid 闁瑰瓨鍔曢幉?UUID
     * @return 闁告瑯鍨抽弫銈嗙鎼达絾娅曢梻鍫涘灩閻秶绮堥搹瑙勭暠闁瑰瓨鍔曢幉鎶藉触瀹ュ泦鐐烘晬濞戞粌顏熸繛灞稿墲濠€浣虹磽閹惧磭鎽犻柛姘Ф琚ㄩ柛鎺撶懃濞叉牠鏌呴埀顒佺▔?UUID
     */
    private String resolveProjectMemberDisplayName(Project project, String memberUuid) {
        if (project == null || memberUuid == null || memberUuid.isBlank()) {
            return "";
        }
        String displayName = project.getMemberName(memberUuid);
        if (displayName != null && !displayName.isBlank()) {
            displayName = displayName.trim();
        }
        try {
            if (minecraft != null && minecraft.getConnection() != null) {
                net.minecraft.client.multiplayer.PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(UUID.fromString(memberUuid));
                if (playerInfo != null && playerInfo.getProfile() != null) {
                    String onlineName = playerInfo.getProfile().getName();
                    if (onlineName != null && !onlineName.isBlank()) {
                        displayName = onlineName;
                        project.setMemberName(memberUuid, onlineName);
                    }
                }
            }
        } catch (Exception e) {
            // 闊洨鏅弳鎰版閻愬銆?UUID 闁瑰瓨鐗旀径宥夊籍閹壆绠鹃柟鎭掑劤婵悂骞€娴ｅ摜纾介悽顖炴交缁辨繄绱掕閻㈢粯鎷呯捄銊︽殢缂傚倹鎸搁悺銊╁触瀹ュ泦鐐哄箣?UUID 闁稿繑绮岀花鎶藉Υ?
        }
        if (displayName == null || displayName.isBlank()) {
            return memberUuid;
        }
        return displayName;
    }

    /**
     * 濞寸姾顕ф慨鐔煎箰閸ャ劍鐑︾€殿喖婀遍悰銉︾▔椤撶姵鐣遍柟瀛樺姇閹叉娊宕愬▎鎾亾婢舵劑鈧秹鏁嶇仦鑲╃閻庢稒蓱閸ㄦ岸宕?UUID 濞戞挸楠哥紞瀣礈瀹ュ棙鈻旂紒鈧崫鍕€崇紒澶庡焽閳?
     */
    private static final class AssignableMember {
        private final String uuid;
        private final String displayName;

        /**
         * 闁告帗绋戠紓鎾寸▔閳ь剚绋夐鍕闁圭娲﹀ǎ鎶藉箣閹邦剚鍠呴柛濠冪懇閳ь剙顦甸妴宥夊Υ?
         *
         * @param uuid 闁瑰瓨鍔曢幉?UUID
         * @param displayName 闁瑰瓨鍔曢幉鎶藉及閸撗佷粵闁告艾绉惰ⅷ
         */
        private AssignableMember(String uuid, String displayName) {
            this.uuid = uuid;
            this.displayName = displayName;
        }
    }

    private void filterTasks(String filter) {
        currentFilter = "active";
        List<Task> result = taskManager == null ? new ArrayList<>() : taskManager.getIncompleteTasks();

        if (currentPriorityFilter != 0) {
            Task.Priority targetPriority = Task.Priority.MEDIUM;
            if (currentPriorityFilter == 1) targetPriority = Task.Priority.HIGH;
            else if (currentPriorityFilter == 2) targetPriority = Task.Priority.MEDIUM;
            else if (currentPriorityFilter == 3) targetPriority = Task.Priority.LOW;

            List<Task> priorityFiltered = new ArrayList<>();
            for (Task t : result) {
                if (t.getPriority() == targetPriority) {
                    priorityFiltered.add(t);
                }
            }
            result = priorityFiltered;
        }

        baseFilteredTasks = applyAssignedFilterIfNeeded(result);
        applySearchFilter();
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        updateViewButtonsState();
    }

    private void refreshTaskList() {
        filterTasks("active");
    }

    private void applySearchFilter() {
        if (baseFilteredTasks == null) {
            baseFilteredTasks = new ArrayList<>();
        }
        if (searchQuery == null || searchQuery.isEmpty()) {
            filteredTasks = new ArrayList<>(baseFilteredTasks);
        } else {
            String q = searchQuery;
            List<Task> result = new ArrayList<>();
            for (Task task : baseFilteredTasks) {
                String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
                String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
                boolean matchText = title.contains(q) || desc.contains(q);
                boolean matchTag = false;
                for (String tag : task.getTags()) {
                    if (tag != null && tag.toLowerCase().contains(q)) {
                        matchTag = true;
                        break;
                    }
                }
                if (matchText || matchTag) {
                    result.add(task);
                }
            }
            filteredTasks = result;
        }
        if (taskListWidget != null) {
            taskListWidget.setTaskReorderEnabled(isTaskReorderAllowedInCurrentView());
            taskListWidget.setSections(buildTaskPaneSections());
        }
    }

    /**
     * 闁哄瀚紓鎾广亹閹惧啿顤呭ù鐘侯嚙婵喖宕犻崫鍕€绘繛鍫濈仛閺嗙喖骞戦鍡欑濞戞捁銆€閳ь剚绮嶅﹢顓犫偓鐟版湰閸?+ 鐎瑰憡褰冮悾顒勫箣閹邦厼顫戦柛娆戝Т閸ㄥ海绱掗崟銊㈠亾濠靛洤绲瑰〒姘☉閻斺偓缁绢厸鍋撴俊顖椻偓宕団偓鐑藉Υ?     *
     * @return 鐟滅増鎸告晶鐘崇鐠囨彃顫ら柛鏍ф惈閸ㄥ骸鈻撻棃娑樼仚閻?     */
    private List<TaskListWidget.SectionModel> buildTaskPaneSections() {
        List<TaskListWidget.SectionModel> sections = new ArrayList<>();
        List<Task> activeTasks = filteredTasks == null ? List.of() : List.copyOf(filteredTasks);

        sections.add(new TaskListWidget.SectionModel(
                "active",
                Component.translatable("gui.todolist.active").getString(),
                activeTasks,
                false,
                true
        ));

        List<Task> completedTasks = buildCompletedTasksForCurrentView();
        sections.add(new TaskListWidget.SectionModel(
                "completed",
                Component.translatable("gui.todolist.completed").getString() + " (" + completedTasks.size() + ")",
                completedTasks,
                true,
                completedExpanded
        ));
        return sections;
    }

    /**
     * 闁哄瀚紓鎾广亹閹惧啿顤呭銈呮贡濞蹭即宕仦鐣岀Ъ闁告挸绉烽～瀣炊閸欍儳鐟撻柣銊ュ閸戯紕鈧懓鏈崹姘鐠囨彃顫ら柛鎺擃殙閵嗗啴鏁嶅畝鈧弫銈嗙鎼存繂鐦滃ù鐘侯嚙婵喖宕犻崫鍕亢闂侇喓鍔嶆慨宀勫矗閻樻彃鐎荤紓浣稿閳?     *
     * @return 鐟滅増鎸告晶鐘诲矗椤栨繍娼岄柣銊ュ閸戯紕鈧懓鏈崹姘鐠囨彃顫ら柛鎺擃殙閵?
     */
    private List<Task> buildCompletedTasksForCurrentView() {
        if (taskManager == null || currentProject == null) {
            return List.of();
        }
        List<Task> completedTasks = taskManager.getCompletedTasks();
        List<Task> priorityFiltered = applyPriorityFilterToTasks(completedTasks);
        List<Task> scopedTasks = applyAssignedFilterIfNeeded(priorityFiltered);
        return applySearchQueryToTasks(scopedTasks);
    }

    /**
     * 返回当前界面中可拖拽的未完成任务顺序快照，用于判断手动排序是否真正改变了顺序。
     *
     * @return 当前可见未完成任务 ID 顺序
     */
    private List<String> getCurrentVisibleActiveTaskIds() {
        if (filteredTasks == null || filteredTasks.isEmpty()) {
            return List.of();
        }
        return filteredTasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> !task.isCompleted())
                .map(Task::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 閻庝絻顫夌€垫氨鈧鐭幑銏ゅ礉閳ュ啿鐏欓悶娑栧妼椤︽煡鎮介妸銉хЪ闁告挸绉崇槐顓㈠礂閸垽鐛撶紒娑欑洴閳ь剙顦板顖涚闊祴鍋?     *
     * @param source 鐎垫澘鎳愰悺顐︽焻婢跺本鐣卞ù鐘侯嚙婵喖宕氬Δ鍕┾偓?
     * @return 濞村吋锚閸樻稓鐥閻☆偊鏌呮径濠冨€甸柣銊ュ閹广垽宕濋垾鍐茬仚閻?     */
    private List<Task> applyPriorityFilterToTasks(List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        if (currentPriorityFilter == 0) {
            return new ArrayList<>(input);
        }
        Task.Priority targetPriority = Task.Priority.MEDIUM;
        if (currentPriorityFilter == 1) {
            targetPriority = Task.Priority.HIGH;
        } else if (currentPriorityFilter == 2) {
            targetPriority = Task.Priority.MEDIUM;
        } else if (currentPriorityFilter == 3) {
            targetPriority = Task.Priority.LOW;
        }
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.getPriority() == targetPriority) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 閻庝絻顫夌€垫氨鈧鐭幑銏ゅ礉閳ュ啿鐏欓悶娑栧妼椤︽煡鎮介妸銉хЪ闁告挸绉甸幃宕囨閵忕姴褰犻梺娆惧枛閻⊙囧Υ?     *
     * @param source 鐎垫澘鎳愰悺顐︽焻婢跺本鐣卞ù鐘侯嚙婵喖宕氬Δ鍕┾偓?
     * @return 闁瑰吋绮庨崒銊х驳濞戔懇鍋撴径濠冨€甸柣銊ュ閹广垽宕濋垾鍐茬仚閻?     */
    private List<Task> applySearchQueryToTasks(List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new ArrayList<>(input);
        }
        String q = searchQuery;
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task == null) {
                continue;
            }
            String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
            String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
            boolean matchText = title.contains(q) || desc.contains(q);
            boolean matchTag = false;
            for (String tag : task.getTags()) {
                if (tag != null && tag.toLowerCase().contains(q)) {
                    matchTag = true;
                    break;
                }
            }
            if (matchText || matchTag) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 閻忓繐妫欑敮鑸电閹増绁柟璇℃線鐠愮喓绱掗悢鍓侇伇闁汇劌瀚粩鐔兼偩鐏炵偓娈剁紓浣稿缁辨繃绗熷☉娆戙偞閻犲洦娲戦崬顒勬儘娴ｇ瓔鍤㈤柛娆愮墪缁旈浠﹂埀顒佺┍閳╁啩绱栭柕?     *
     * @param widget 闁烩晩鍠楅悥锝夊箳瑜屽▎?
     * @return 濞撴碍绻冮濂稿礌閸涱厽鍎?x闁靛棔绨滈柕鍡曠皻idth闁靛棔寮揺ight 闁汇劌瀚粩鐔兼偩鐏炵偓娈剁紓?     */
    private int[] toWidgetBounds(net.minecraft.client.gui.components.AbstractWidget widget) {
        if (widget == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()};
    }

    private String getFieldValue(EditBox field, String hint) {
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) return "";
        if (!hint.isEmpty() && raw.equals(hint)) return "";
        return raw;
    }

    private String getFieldValue(MultiLineEditBox field, String hint) {
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) return "";
        if (!hint.isEmpty() && raw.equals(hint)) return "";
        return raw;
    }

    private ViewMode parseHudViewMode(String raw) {
        if (raw == null) {
            return ViewMode.PERSONAL;
        }
        String v = raw.trim().toUpperCase();
        if ("TEAM_UNASSIGNED".equals(v)) return ViewMode.TEAM_UNASSIGNED;
        if ("TEAM_ALL".equals(v)) return ViewMode.TEAM_ALL;
        if ("TEAM_ASSIGNED".equals(v)) return ViewMode.TEAM_ASSIGNED;
        return ViewMode.PERSONAL;
    }

    private void addNotification(String text) {
        long now = System.currentTimeMillis();
        notifications.add(new Notification(text, now + 2000));
    }

    private static class Notification {
        final String text;
        final long expireAt;

        Notification(String text, long expireAt) {
            this.text = text;
            this.expireAt = expireAt;
        }
    }

    private static int clampInt(int value, int min, int max) {
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
    
    private List<Task> applyAssignedFilterIfNeeded(List<Task> tasks) {
        if (currentProject == null) {
            return new ArrayList<>();
        }
        List<Task> projectFiltered = new ArrayList<>();
        for (Task t : tasks) {
            if (t.belongsToProject(currentProject.getId())) {
                projectFiltered.add(t);
            }
        }
        
        List<Task> result = new ArrayList<>();
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            if (this.minecraft != null && this.minecraft.player != null) {
                String myUuid = this.minecraft.player.getUUID().toString();
                for (Task t : projectFiltered) {
                    if (myUuid.equals(t.getAssigneeUuid())) {
                        result.add(t);
                    }
                }
            }
            return result;
        } else if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            for (Task t : projectFiltered) {
                String assignee = t.getAssigneeUuid();
                if (assignee == null || assignee.isEmpty()) {
                    result.add(t);
                }
            }
            return result;
        } else if (viewMode == ViewMode.TEAM_ALL) {
            for (Task t : projectFiltered) {
                String assignee = t.getAssigneeUuid();
                if (assignee != null && !assignee.isEmpty()) {
                    result.add(t);
                }
            }
            return result;
        }
        return projectFiltered;
    }
    
    private boolean isTrueSingleplayer() {
        return false;
    }
    
    private void switchView(ViewMode mode) {
        this.viewMode = mode;
        syncViewStateForCurrentProject();
        clearSelectedTask();
        ModConfig config = ModConfig.getInstance();
        if (this.viewMode == ViewMode.PERSONAL) {
            config.setHudDefaultView("PERSONAL");
        } else {
            config.setHudDefaultView(this.viewMode.name());
        }
        updateViewButtonsState();
        rebuildUI();
    }

    private void syncHudViewForProject(Project project) {
        ModConfig config = ModConfig.getInstance();
        currentSpaceMode = resolveSpaceMode(project);
        if (currentSpaceMode == SpaceMode.PERSONAL) {
            currentTaskViewOption = TaskViewOption.MY;
            syncLegacyViewModeFromState();
            config.setHudDefaultView("PERSONAL");
            return;
        }
        ViewMode configView = parseHudViewMode(config.getHudDefaultView());
        TaskViewOption resolvedOption = configView == ViewMode.PERSONAL
                ? resolveDefaultViewForSpace(currentSpaceMode)
                : resolveTaskViewOptionFromLegacy(configView);
        if (!buildVisibleViewOptions(currentSpaceMode).contains(resolvedOption)) {
            resolvedOption = resolveDefaultViewForSpace(currentSpaceMode);
        }
        currentTaskViewOption = resolvedOption;
        syncLegacyViewModeFromState();
        config.setHudDefaultView(this.viewMode.name());
    }
    
    private void updateViewButtonsState() {
        if (personalSpaceButton != null) {
            personalSpaceButton.active = currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.active = teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
            myViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            unassignedViewButton.setMessage(Component.literal("\u5f85\u5206\u914d"));
            unassignedViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.setMessage(Component.translatable("gui.todolist.all"));
            allViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.ALL;
        }
    }
    
    private void switchProject(Project project) {
        if (projectListWidget != null) {
            savedProjectListScrollOffset = projectListWidget.getScrollOffset();
        }
        this.selectedTask = null;
        this.detailOverlayVisible = false;
        this.sidebarOverlayVisible = false;
        this.currentProject = project;
        rememberSelectedProject(project);

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            syncLegacyViewModeFromState();
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            syncHudViewForProject(null);
            rebuildUI();
            return;
        }

        if (!teamProjectsEnabled && project.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        projectScopeFilter = project.getScope();
        ClientBridge.ops().setActiveProjectId(project.getId());
        ClientBridge.ops().sendSetActiveProjectId(project.getId());
        ClientBridge.saveLastActiveProjectId(project.getId());
        
        if (project.getScope() == Project.Scope.PERSONAL) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            syncLegacyViewModeFromState();
        } else {
            this.taskManager = this.teamTaskManager;
            currentSpaceMode = SpaceMode.TEAM;
            if (currentTaskViewOption == TaskViewOption.MY && viewMode == ViewMode.PERSONAL) {
                currentTaskViewOption = TaskViewOption.UNASSIGNED;
            }
            syncLegacyViewModeFromState();
        }
        syncHudViewForProject(project);
        
        rebuildUI();
    }

    /**
     * 鐟滅増鎹囬妴宥夋儎椤旂厧鐏╅梻鍕╁€栧鍌炴晬瀹€鈧悵娑㈠础閸忓懐鐭ら柡鍫墮濠€瀛樼鐠囨彃顫ょ紒鐙呯磿閹﹪宕抽妸褉鈧牠宕氶悩缁樼彑闁稿繐鐤囨禒鍫熺鐠囨彃顫ら柕?
     */
    private void hardDeleteTasksForDeletedProject(Project deletedProject) {
        if (deletedProject == null || deletedProject.getId() == null || deletedProject.getId().isEmpty()) {
            return;
        }
        String deletedProjectId = deletedProject.getId();
        hardDeleteProjectTasksInManager(personalTaskManager, deletedProjectId);
        hardDeleteProjectTasksInManager(teamTaskManager, deletedProjectId);
    }

    /**
     * 闁革负鍔嶇€垫氨鈧鐭幑銏ゅ礉閿涘嫷鍚€闁荤偛妫楀▍鎺撶▔椤撶偛鐏╅梻鍕╁€楀ú浼村冀閸ヮ兙鈧秹鎯勯鑽ょ憮闁稿繈鍔戦崕瀛樼鐠囨彃顫ら柕?
     */
    private void hardDeleteProjectTasksInManager(TaskManager manager, String deletedProjectId) {
        if (manager == null || deletedProjectId == null || deletedProjectId.isEmpty()) {
            return;
        }
        manager.deleteTasksByProjectId(deletedProjectId);
    }

    private void rememberSelectedProject(Project project) {
        if (project == null || project.getId() == null || project.getId().isEmpty()) {
            return;
        }
        if (project.getScope() == Project.Scope.TEAM) {
            preferredTeamProjectId = project.getId();
        } else {
            preferredPersonalProjectId = project.getId();
        }
    }

    private Project getPreferredProjectForScope(Project.Scope scope) {
        if (scope == null) {
            return null;
        }
        if (scope == Project.Scope.TEAM && !teamProjectsEnabled) {
            return null;
        }
        String preferredId = scope == Project.Scope.TEAM ? preferredTeamProjectId : preferredPersonalProjectId;
        if (preferredId != null && !preferredId.isEmpty()) {
            Project preferred = projectManager.getProject(preferredId);
            if (preferred != null && preferred.getScope() == scope) {
                return preferred;
            }
        }
        List<Project> projects = projectManager.getProjectsByScope(scope);
        if (projects.isEmpty()) {
            return null;
        }
        for (Project p : projects) {
            if (scope == Project.Scope.PERSONAL && p.isDefaultPersonalProject()) {
                return p;
            }
            if (scope == Project.Scope.TEAM && p.isDefaultTeamProject()) {
                return p;
            }
        }
        return projects.get(0);
    }
    
    private void onAddProject() {
        Project.Scope defaultScope = Project.Scope.PERSONAL;
        if (currentProject != null) {
            defaultScope = currentProject.getScope();
        } else if (projectScopeFilter != null) {
            defaultScope = projectScopeFilter;
        }
        if (defaultScope == Project.Scope.TEAM && !ClientBridge.ops().isTeamProjectsEnabled()) {
            defaultScope = Project.Scope.PERSONAL;
        }
        minecraft.setScreen(new AddProjectScreen(this, defaultScope));
    }
    
    private void onProjectSettings() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        minecraft.setScreen(new ProjectSettingsScreen(this, currentProject));
    }

    private boolean canDeleteCurrentProject() {
        if (currentProject == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (currentProject.isDefaultPersonalProject() || currentProject.isDefaultTeamProject()) {
            return false;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        if (currentProject.getScope() == Project.Scope.PERSONAL) {
            String owner = currentProject.getOwnerUuid();
            return owner == null || owner.isEmpty() || owner.equals(uuid);
        }
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.DELETE_PROJECT, role, ctx);
    }

    private void onApplyJoinProject() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            return;
        }
        if (isCurrentPlayerProjectMember()) {
            return;
        }
        ClientBridge.ops().sendRequestJoinProject(currentProject.getId());
        addNotification(Component.translatable("message.todolist.project.join.sent").getString());
    }

    private void onProjectDelete() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (!canDeleteCurrentProject()) {
            addNotification(Component.translatable("message.todolist.no_permission_delete_project").getString());
            return;
        }
        String projectName = ProjectNameFormatter.toDisplayText(currentProject).getString();
        Component message = Component.translatable("gui.todolist.project.delete_confirm.message", projectName);
        minecraft.setScreen(new ConfirmDeleteProjectScreen(this, message, () -> {
            ClientBridge.ops().sendDeleteProject(currentProject.getId());
        }));
    }

    private class AssignPlayerScreen extends Screen {
        private final TodoScreen parentScreen;
        private final Task targetTask;
        private EditBox searchField;
        private List<AssignableMember> allMembers;
        private List<AssignableMember> filteredMembers;
        private Button[] playerButtons;
        private Button cancelButton;
        private MemberSelectionDialogLayout dialogLayout;
        private int scrollOffset;
        private int visibleRows;
        private int listX;
        private int listY;
        private int listWidth;
        private int listHeight;
        private int rowHeight;

        protected AssignPlayerScreen(TodoScreen parentScreen, Task targetTask) {
            super(Component.translatable("gui.todolist.assign_others"));
            this.parentScreen = parentScreen;
            this.targetTask = targetTask;
        }

        @Override
        protected void init() {
            super.init();
            if (minecraft == null) {
                return;
            }
            dialogLayout = buildDialogLayout();
            applyDialogLayout(dialogLayout);

            searchField = new EditBox(this.font, dialogLayout.dialogX(), dialogLayout.searchY(),
                    dialogLayout.dialogWidth(), dialogLayout.searchHeight(), Component.empty());
            searchField.setHint(Component.translatable("gui.todolist.member.name"));
            searchField.setValue("");
            this.addRenderableWidget(searchField);

            allMembers = collectAssignableMembers();
            filteredMembers = new ArrayList<>();

            playerButtons = new Button[visibleRows];
            for (int i = 0; i < visibleRows; i++) {
                int btnY = listY + i * rowHeight;
                final int rowIndex = i;
                Button btn = Button.builder(Component.empty(), b -> {
                    AssignableMember entry = getMemberForRow(rowIndex);
                    if (entry != null) {
                        applyAssignTo(entry.uuid, entry.displayName);
                    }
                }).bounds(dialogLayout.dialogX(), btnY, dialogLayout.dialogWidth(), 20).build();
                btn.active = false;
                btn.visible = false;
                this.addRenderableWidget(btn);
                playerButtons[i] = btn;
            }

            cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> {
                minecraft.setScreen(parentScreen);
            }).bounds(dialogLayout.cancelX(), dialogLayout.cancelY(),
                    dialogLayout.cancelWidth(), dialogLayout.cancelHeight()).build();
            this.addRenderableWidget(cancelButton);

            searchField.setResponder(text -> {
                updateFilteredPlayers();
            });
            updateFilteredPlayers();
            this.setFocused(searchField);
        }

        /**
         * 闁哄瀚紓鎾广亹閹惧啿顤呯紒鎰殔瑜版稓浜搁崫鍕靛殶濞戞挸顑囧▓鎴炵鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊閻㈩垰鍟惇顒勫Υ?         *
         * @return 闁告繂绉寸花鎻掝嚕韫囨挾顏撮悘鐐╁亾闊浂鍋嗛崣?
         */
        private MemberSelectionDialogLayout buildDialogLayout() {
            return MemberSelectionDialogLayout.create(this.width, this.height);
        }

        /**
         * 閻忓繐妫楃粩椋庝沪閳ь剝绠涢銈呭季濞戞搩鍘惧▓鎴﹀锤閹邦厾鍨奸柛姘湰椤掔偤宕氶弶璺ㄧЪ闁告挸绉堕弲顐︽閵忕姷鎽熸繛鍫㈩暜缁辨繃绗熷☉娆掝洬闁哄本鎸堕埀顑跨劍缁挳宕濋妸銈囩憿婵炴潙顑堥惁顖涘緞瀹ュ洦鏆忛柕?         *
         * @param layout 鐟滅増鎸告晶鐘电玻濡も偓瑜版稒绋夌€ｎ剚鐣卞ù鐘侯嚙婵喖宕氶崱娑樺赋鐎殿喖婀遍悰銉ф暜閸愩劎婀?
         */
        private void applyDialogLayout(MemberSelectionDialogLayout layout) {
            if (layout == null) {
                return;
            }
            rowHeight = layout.rowHeight();
            visibleRows = layout.visibleRows();
            listWidth = layout.listWidth();
            listX = layout.listX();
            listY = layout.listY();
            listHeight = layout.listHeight();
        }

        /**
         * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喖宕氶崱娑樺赋鐎殿喖婀遍悰銉╂儍閸曨剛娉婇柛鏂诲妼娴滃摜绮旀导娆戠濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担鍦ⅰ濡ょ姴鏈划鎾礉閵娿劎鐝堕柣锝呰閳?         *
         * @return 鐟滅増鎸告晶鐘差煥濮橆剙袟闁稿绻掍簺
         */
        private int getScrollOffsetForTest() {
            return scrollOffset;
        }

        /**
         * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喖宕氶崱娑樺赋鐎殿喖婀遍悰銉╂儍閸曨偄璁查悷娆庢祰椤㈡垿寮敮顔剧濞撴碍绋戦幃鎾诲礌閸涱喚銈撮悹鍥ㄦ磻閸烆剟鎯嶆担绛嬪悁缂佺姵顨嗗〒鑸靛緞瑜庣划鎾礉閵娿劌鐦遍柛銉︾暘閳?         *
         * @return 鐟滅増鎸告晶鐘诲矗椤栨繍娼岄悶娑樻湰閺?
         */
        private int getVisibleRowsForTest() {
            return visibleRows;
        }

        /**
         * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喖宕氶崱娑樺赋鐎殿喖婀遍悰銉╁礆濡ゅ嫨鈧啴宕犻崫鍕幍濞戞搩鍘肩缓楣冩倷?X 闁秆勫姈閻栵綁鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粍銇欓崡鐐残楁繝濠冧亢閻ゅ棙绂嶇€ｂ晜顐介柕?         *
         * @return 闁告帗顨夐妴鍐礌閸濆嫮鍘靛☉鎿冨幖缁洪箖鎮?X 闁秆勫姈閻?
         */
        private double getListCenterXForTest() {
            return dialogLayout == null ? listX + (listWidth / 2.0D) : dialogLayout.getListCenterX();
        }

        /**
         * 閺夆晜鏌ㄥú鏍亹閹惧啿顤呭ù鐘侯嚙婵喖宕氶崱娑樺赋鐎殿喖婀遍悰銉╁礆濡ゅ嫨鈧啴宕犻崫鍕幍濞戞搩鍘肩缓楣冩倷?Y 闁秆勫姈閻栵綁鏁嶇仦鑲╄繑闁告艾鑻€垫ê霉鐎ｎ厾妲稿ù鐙呯悼閻栨粍銇欓崡鐐残楁繝濠冧亢閻ゅ棙绂嶇€ｂ晜顐介柕?         *
         * @return 闁告帗顨夐妴鍐礌閸濆嫮鍘靛☉鎿冨幖缁洪箖鎮?Y 闁秆勫姈閻?
         */
        private double getListCenterYForTest() {
            return dialogLayout == null ? listY + (listHeight / 2.0D) : dialogLayout.getListCenterY();
        }

        /**
         * 闁哄瀚紓鎾广亹閹惧啿顤呴柛銉ｅ灲濡诧附銇勯崷顓熺獥闁告瑯鍨欢鐢稿箰閸ャ劍鐑﹂柣銊ュ閸ㄦ岸宕ㄥΟ鍝勭仚閻炴侗鐓夌槐婵嬪礌閸涱厽鍎?owner 濞戞挻鏌ㄩ獮鎾绘煂瀹ュ繒绀夋鐐跺煐鐎垫粎鐥敃鈧悾鐐亜閸濆嫮纰嶇紒瀣暱閻ｇ偓娼忛幘鍐叉瘔闁?         *
         * @return 闁告瑯鍨辩€垫艾煤閻愵剙鐏囬柛娑櫭埀顒佺懇閳ь剙顦崹顏嗘偘?
         */
        private List<AssignableMember> collectAssignableMembers() {
            List<AssignableMember> members = new ArrayList<>();
            if (currentProject == null || currentProject.getScope() != Project.Scope.TEAM) {
                return members;
            }
            String ownerUuid = currentProject.getOwnerUuid();
            if (ownerUuid != null && !ownerUuid.isBlank()) {
                members.add(new AssignableMember(ownerUuid, resolveProjectMemberDisplayName(currentProject, ownerUuid)));
            }
            List<AssignableMember> otherMembers = new ArrayList<>();
            for (String memberUuid : currentProject.getMembers().keySet()) {
                if (memberUuid == null || memberUuid.isBlank()) {
                    continue;
                }
                if (memberUuid.equals(ownerUuid)) {
                    continue;
                }
                otherMembers.add(new AssignableMember(memberUuid, resolveProjectMemberDisplayName(currentProject, memberUuid)));
            }
            otherMembers.sort(Comparator.comparing(member -> member.displayName, String.CASE_INSENSITIVE_ORDER));
            members.addAll(otherMembers);
            return members;
        }

        /**
         * 閺夆晜鏌ㄥú鏍箰閸パ呮毎閻炴稑鑻紞瀣礈瀹ュ拋鍤犻幖瀛樻⒒濞堟垿骞嬮幇顒佸枀闁稿﹥鐟╅埀顒€顦甸妴宥夊Υ?
         *
         * @param rowIndex 閻炴稑鐬奸崒銊ヮ嚕?
         * @return 鐟滅増鎸告晶鐘垫偘鐏炴儳鐏囬柛娑欙公缁遍亶鎳熼妷銊ㄩ柣锝呰嫰閸垱娼婚弬鎸庣 null
         */
        private AssignableMember getMemberForRow(int rowIndex) {
            if (filteredMembers == null || filteredMembers.isEmpty()) {
                return null;
            }
            int index = scrollOffset + rowIndex;
            if (index < 0 || index >= filteredMembers.size()) {
                return null;
            }
            return filteredMembers.get(index);
        }

        /**
         * 闁圭顦伴幃宕囨閵忕姴褰犻梺娆惧枛閻⊙勬交閸ャ劍濮㈤柛娆樺灡鐎垫艾煤閻愵剙鐏囬柛娑櫭崹顏嗘偘閵婏絺鍋?
         */
        private void updateFilteredPlayers() {
            if (allMembers == null || filteredMembers == null) {
                return;
            }
            filteredMembers.clear();
            String query = searchField == null ? "" : searchField.getValue();
            if (query == null) {
                query = "";
            }
            String q = query.trim().toLowerCase();
            for (AssignableMember entry : allMembers) {
                String name = entry.displayName;
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    filteredMembers.add(entry);
                }
            }
            scrollOffset = 0;
            updatePlayerButtons();
        }

        /**
         * 闁哄秷顫夊畵浣姐亹閹惧啿顤呮繝濠冭壘婵晜鎷呭鍥╂瀭闁告帡鏀遍弻濠囧箣閹邦剚鍠呴柟绋款樀閹告娊寮崶顭戞敵濞戞挸楠歌ぐ鑼喆娴ｅ厜鍋撹閳?
         */
        private void updatePlayerButtons() {
            if (playerButtons == null) {
                return;
            }
            scrollOffset = clampMemberScrollOffset();
            for (int i = 0; i < playerButtons.length; i++) {
                Button btn = playerButtons[i];
                AssignableMember entry = getMemberForRow(i);
                if (entry == null) {
                    btn.visible = false;
                    btn.active = false;
                    btn.setMessage(Component.empty());
                } else {
                    btn.visible = true;
                    btn.active = true;
                    btn.setMessage(Component.nullToEmpty(entry.displayName));
                }
            }
        }

        /**
         * 閻忓繐妫旈幑銏ゅ礉閳ュ啿鐎婚梺鏉跨Т閼村﹦绮ｅΔ鍐╃暠婵犲﹥鑹炬慨鈺呭磻韫囨泤鈺呮⒔閹邦剙鐓戦柛锔哄妼閳ь剚鐟╅埀顒€顦伴崹姘跺川濡搫鐏欓悶娑栧妿濞堟垿寮垫径瀣珡闁肩厧鍟ú鍧楀礃閸涱偀鍋?         *
         * @return 濞ｅ浂鍠楅婊堝触鎼达絾鐣辨繝濠冭壘婵晠宕戣箛鏇?
         */
        private int clampMemberScrollOffset() {
            int totalItems = filteredMembers == null ? 0 : filteredMembers.size();
            return MemberSelectionDialogLayout.clampScrollOffset(scrollOffset, totalItems, visibleRows);
        }

        /**
         * 閺夆晜鏌ㄥú鏍ㄧ鐠囨彃顫ら柛鎺戞閸樸倕顕ｉ崷顓犲炊闁稿﹥鐟╅埀顒€顦伴崹姘跺川濡搫鐏欓悶娑栧妼閸樻垹鎷嬮崫銉︾暠闁哄牃鍋撳鍫嗗嫮娉婇柛鏂诲妼娴滃摜绮斿Ч鍥ｅ亾?         *
         * @return 闁哄牃鍋撳鍫嗗嫮娉婇柛鏂诲妼娴滃摜绮?         */
        private int getMaxMemberScrollOffset() {
            int totalItems = filteredMembers == null ? 0 : filteredMembers.size();
            return MemberSelectionDialogLayout.getMaxScrollOffset(totalItems, visibleRows);
        }

        /**
         * 閻忓繐妫涘ú浼村冀閸ワ附宕查柛鏂哄墲鐎垫艾煤閸撗呰埗闂侇偄顦懙鎴︽儍閸曨偅绀嬮梻鍐枑閸ㄦ岸宕ㄥ鍫㈢妤犵偠娉涘ú鏍礆閹殿喖鐓戦柣锝呯焸濞间即濡?
         *
         * @param uuid 闁烩晩鍠楅悥锝夊箣閹邦剚鍠?UUID
         * @param name 闁烩晩鍠楅悥锝夊箣閹邦剚鍠呴柡鍕⒔閵囨岸宕ュ鍥?
         */
        private void applyAssignTo(String uuid, String name) {
            targetTask.setAssigneeUuid(uuid);
            targetTask.setAssigneeName(name);
            parentScreen.addNotification(Component.translatable("message.todolist.assigned_to_player", name).getString());
            parentScreen.markUnsaved();
            parentScreen.refreshTaskList();
            minecraft.setScreen(parentScreen);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (dialogLayout != null && dialogLayout.isInsideList(mouseX, mouseY)) {
                if (filteredMembers != null && !filteredMembers.isEmpty()) {
                    int maxOffset = getMaxMemberScrollOffset();
                    if (amount < 0 && scrollOffset < maxOffset) {
                        scrollOffset++;
                        updatePlayerButtons();
                    } else if (amount > 0 && scrollOffset > 0) {
                        scrollOffset--;
                        updatePlayerButtons();
                    }
                }
            }
            return super.mouseScrolled(mouseX, mouseY, amount);
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}


