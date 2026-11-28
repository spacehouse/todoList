package com.todolist.importer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 任务导入格式 v1 的解析与转换器：把一份 JSON 导入文档转换为可直接入库的任务列表。
 *
 * <p>处理流程分三步：Gson 反序列化 → 全量校验（收集所有错误）→ 两遍转换
 * （第一遍建任务并登记别名映射；第二遍按别名挂接父子关系）。
 * 校验采用**严格语义**：任何字段非法都会让整批导入失败（原子性），
 * 不会静默降级——与运行期 {@link TaskTrigger.Type#parse} 的容错回退语义刻意不同，
 * 因为导入文件来自外部工具（AI 生成 / JEI 附属 / 手工编写），错误必须显式暴露给生产者修复。
 *
 * <p>导入结果为**纯数据**：不写存储、不发网络包，由调用方（命令层）确认后走现有保存链路
 * （与 {@code MaterialTaskGenerator} 的纯数据约定一致）。
 * 错误明细使用「#任务序号 字段: 原因」的技术格式（面向 JSON 生产者排错），
 * 多语言提示由命令层包装。
 *
 * <p>格式规范见 {@code docs/completed/feat-task-import-format.md}。
 */
public final class TaskImporter {

    /** 当前支持的格式版本号 */
    public static final int SUPPORTED_FORMAT_VERSION = 1;

    /** 单次导入任务数上限，防止误导入巨型文件拖垮存储与网络同步 */
    public static final int MAX_TASKS = 500;

    /** 校验失败时向玩家回报的错误明细条数上限 */
    public static final int MAX_ERRORS_REPORTED = 5;

    /** 任务标题长度上限，与 H2 tasks.title 列宽一致 */
    public static final int MAX_TITLE_LENGTH = 512;

    /** 任务描述长度上限，与 H2 tasks.description 列宽一致 */
    public static final int MAX_DESCRIPTION_LENGTH = 16384;

    /** 单个标签长度上限，与 H2 task_tags.tag 列宽一致 */
    public static final int MAX_TAG_LENGTH = 128;

    /** 触发器目标长度上限，与 H2 tasks.trigger_target 列宽一致 */
    public static final int MAX_TRIGGER_TARGET_LENGTH = 256;

    /** 导入文件大小上限：超限直接拒绝，避免读入巨型文件造成内存压力 */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** 批次文本总量预算（标题+描述+标签的 UTF-8 字节数）：个人任务同步是单包全量，防超网络帧上限 */
    public static final int MAX_BATCH_TEXT_BYTES = 256 * 1024;

    /** 错误消息中原始值的截断长度，防止超长字段值刷屏聊天 */
    private static final int EXCERPT_LENGTH = 60;

    /** UTF-8 BOM 字节序列，文件开头出现时剥离后再解析 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * 工具类不需要实例化。
     */
    private TaskImporter() {
    }

    /**
     * 导入结果：成功时 {@link #errors()} 为空且 {@link #tasks()} 非空；
     * 失败时 {@link #tasks()} 为空列表，{@link #errors()} 携带全部问题
     * （超出 {@link #MAX_ERRORS_REPORTED} 时末尾附总数提示）。
     *
     * <p>{@link #documentErrorCode()} 区分「文档级错误」（读文件 / JSON 语法 / 版本不支持 /
     * 任务列表为空 / 超上限，此时 errors 恰有一条）与「字段校验错误」（多条，
     * 需逐条反馈给 JSON 生产者）：前者代码见常量 {@code ERROR_*}，后者为 null。
     */
    public record Result(List<Task> tasks, List<String> errors, String documentErrorCode) {

        /** 文档级错误码：文件读取失败 */
        public static final String ERROR_FILE = "file";
        /** 文档级错误码：JSON 语法或结构无法解析 */
        public static final String ERROR_FORMAT = "format";
        /** 文档级错误码：任务列表为空 */
        public static final String ERROR_EMPTY = "empty";
        /** 文档级错误码：任务数超过上限 */
        public static final String ERROR_TOO_MANY = "too_many";
        /** 文档级错误码：文件或内容总量超出大小限制 */
        public static final String ERROR_TOO_LARGE = "too_large";

        /**
         * 判断本次导入是否成功。
         *
         * @return 成功返回 true
         */
        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }

    /**
     * 导入上下文：全部导入任务共用的归属信息。
     *
     * @param creatorUuid 创建者（执行导入命令的玩家）UUID，可为空（测试场景）
     * @param projectId   目标项目 ID，可为空（落到未分组个人任务）
     */
    public record ImportContext(String creatorUuid, String projectId) {
    }

    /**
     * 从文件读取并导入任务。
     *
     * <p>读取前先做文件大小预检（{@link #MAX_FILE_BYTES}），防止巨型文件读入内存；
     * 文件开头带 UTF-8 BOM 时剥离后再解析；解析深度失控（StackOverflowError）
     * 被捕获并按格式错误返回，保证命令线程不会因异常输入崩溃。
     *
     * @param file    导入文件路径
     * @param context 导入上下文
     * @return 导入结果；文件读取失败时按单条错误返回
     */
    public static Result parse(Path file, ImportContext context) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException exception) {
            return failure(Result.ERROR_FILE, "#0 file: " + excerpt(exception.getMessage()));
        }
        if (bytes.length > MAX_FILE_BYTES) {
            return failure(Result.ERROR_TOO_LARGE,
                    "#0 file: size " + bytes.length + " bytes exceeds limit " + MAX_FILE_BYTES);
        }
        int offset = hasUtf8Bom(bytes) ? UTF8_BOM.length : 0;
        try (JsonReader jsonReader = new JsonReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(bytes, offset, bytes.length - offset), StandardCharsets.UTF_8))) {
            return importTasks(readDocument(jsonReader), context);
        } catch (IOException | JsonParseException | StackOverflowError exception) {
            return failure(Result.ERROR_FORMAT, "#0 file: " + excerpt(exception.getMessage()));
        }
    }

    /**
     * 从 JSON 文本导入任务（测试与命令层复用入口）。
     *
     * <p>文本开头的 UTF-8 BOM 字符会被剥离；解析深度失控（StackOverflowError）
     * 被捕获并按格式错误返回。
     *
     * @param json    导入文档 JSON 文本
     * @param context 导入上下文
     * @return 导入结果
     */
    public static Result importTasks(String json, ImportContext context) {
        String normalized = json == null ? "" : stripUtf8BomChar(json);
        try (JsonReader jsonReader = new JsonReader(new java.io.StringReader(normalized))) {
            return importTasks(readDocument(jsonReader), context);
        } catch (IOException | JsonParseException | StackOverflowError exception) {
            return failure(Result.ERROR_FORMAT, "#0 file: " + excerpt(exception.getMessage()));
        }
    }

    /**
     * 判断字节序列是否以 UTF-8 BOM 开头。
     *
     * @param bytes 文件字节
     * @return 带 BOM 时返回 true
     */
    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
    }

    /**
     * 剥离文本开头的 UTF-8 BOM 字符（U+FEFF）。
     *
     * @param text 原始文本
     * @return 剥离后的文本
     */
    private static String stripUtf8BomChar(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /**
     * 反序列化导入文档，统一把 Gson 语法 / 结构错误包装为 JsonParseException。
     *
     * @param reader JSON 读取器（调用方负责关闭）
     * @return 反序列化后的文档；输入为空文档时为 null
     * @throws IOException 读取失败
     * @throws JsonParseException JSON 语法或字段结构非法
     */
    private static TaskImportModels.Document readDocument(JsonReader reader) throws IOException {
        return GSON.fromJson(reader, TaskImportModels.Document.class);
    }

    /**
     * 校验并转换导入文档。
     *
     * @param document 反序列化后的文档，可为 null（空文件）
     * @param context  导入上下文
     * @return 导入结果
     */
    private static Result importTasks(TaskImportModels.Document document, ImportContext context) {
        if (document == null) {
            return failure(Result.ERROR_FORMAT, "#0 file: document is empty");
        }
        if (document.formatVersion == null) {
            return failure(Result.ERROR_FORMAT, "#0 formatVersion: must be present");
        }
        if (document.formatVersion != SUPPORTED_FORMAT_VERSION) {
            return failure(Result.ERROR_FORMAT, "#0 formatVersion: expected " + SUPPORTED_FORMAT_VERSION
                    + " but got " + document.formatVersion);
        }
        List<TaskImportModels.TaskItem> items = document.tasks;
        if (items == null || items.isEmpty()) {
            return failure(Result.ERROR_EMPTY, "#0 tasks: must not be empty");
        }
        if (items.size() > MAX_TASKS) {
            return failure(Result.ERROR_TOO_MANY, "#0 tasks: size " + items.size() + " exceeds limit " + MAX_TASKS);
        }
        List<String> errors = new ArrayList<>();
        Map<String, Integer> aliasIndexById = collectAliases(items, errors);
        for (int index = 0; index < items.size(); index++) {
            validateItemAt(index + 1, items.get(index), aliasIndexById, errors);
        }
        validateNoParentCycles(items, aliasIndexById, errors);
        if (!errors.isEmpty()) {
            return new Result(List.of(), summarizeErrors(errors), null);
        }
        if (batchTextBytes(items) > MAX_BATCH_TEXT_BYTES) {
            return failure(Result.ERROR_TOO_LARGE,
                    "#0 tasks: combined text size exceeds limit " + MAX_BATCH_TEXT_BYTES + " bytes");
        }
        return new Result(convert(items, aliasIndexById, context), List.of(), null);
    }

    /**
     * 第一遍扫描：登记全部本地别名，重复别名记为错误。
     *
     * <p>别名集合先于逐条校验建立，因此 parentId 允许前向引用
     * （父任务别名可以声明在子任务之后）。
     *
     * @param items  任务项列表
     * @param errors 输出：错误收集
     * @return 别名 → 任务序号（0 基）映射；无别名任务不出现在映射中
     */
    private static Map<String, Integer> collectAliases(List<TaskImportModels.TaskItem> items, List<String> errors) {
        Map<String, Integer> aliasIndexById = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            TaskImportModels.TaskItem item = items.get(index);
            if (item == null) {
                errors.add("#" + (index + 1) + " tasks: entry must not be null");
                continue;
            }
            String alias = normalizedOrNull(item.id);
            if (alias == null) {
                continue;
            }
            Integer previous = aliasIndexById.put(alias, index);
            if (previous != null) {
                errors.add("#" + (index + 1) + " id: duplicate alias '" + excerpt(alias)
                        + "' (already used by task #" + (previous + 1) + ")");
            }
        }
        return aliasIndexById;
    }

    /**
     * 校验单条任务项，发现的问题全部记入 errors（不因单条失败中断）。
     *
     * <p>长度上限与 H2 对应列宽一致（title 512 / description 16384 / tag 128 /
     * trigger.target 256），超限值若直接落库会在保存时触发 SQLException。
     *
     * @param index         任务序号（1 基，用于错误定位）
     * @param item          任务项
     * @param aliasIndexById 别名映射
     * @param errors        输出：错误收集
     */
    private static void validateItemAt(int index,
                                       TaskImportModels.TaskItem item,
                                       Map<String, Integer> aliasIndexById,
                                       List<String> errors) {
        if (item == null) {
            // collectAliases 已按序号报告过 null 条目，这里直接跳过防止 NPE
            return;
        }
        String title = normalizedOrNull(item.title);
        if (title == null) {
            errors.add("#" + index + " title: must not be blank");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            errors.add("#" + index + " title: length " + title.length() + " exceeds limit " + MAX_TITLE_LENGTH);
        }
        String description = normalizedOrEmpty(item.description);
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("#" + index + " description: length " + description.length()
                    + " exceeds limit " + MAX_DESCRIPTION_LENGTH);
        }
        if (item.priority != null && parsePriority(item.priority) == null) {
            errors.add("#" + index + " priority: expected one of LOW / MEDIUM / HIGH but got '"
                    + excerpt(item.priority) + "'");
        }
        String parentId = normalizedOrNull(item.parentId);
        if (parentId != null && !aliasIndexById.containsKey(parentId)) {
            errors.add("#" + index + " parentId: unknown alias '" + excerpt(parentId) + "'");
        }
        if (item.tags != null) {
            for (String tag : item.tags) {
                String normalizedTag = normalizedOrNull(tag);
                if (normalizedTag != null && normalizedTag.length() > MAX_TAG_LENGTH) {
                    errors.add("#" + index + " tags: tag length " + normalizedTag.length()
                            + " exceeds limit " + MAX_TAG_LENGTH);
                }
            }
        }
        if (item.dueDate != null && item.dueDate < 0) {
            errors.add("#" + index + " dueDate: must not be negative");
        }
        validateTriggerAt(index, item.trigger, errors);
    }

    /**
     * 检测 parentId 别名链中的自引用与循环引用（如 a→b→a、a→a）。
     *
     * <p>别名图是「每任务至多一个父」的函数图：带三色标记迭代上溯，
     * 回遇到本轮路径中的节点即报环（每个环只报一次，指向环的入口任务）。
     * 环不拦截会导致落库后任务不可见、级联完成等遍历逻辑有死循环风险。
     *
     * @param items          任务项列表
     * @param aliasIndexById 别名 → 任务序号映射
     * @param errors         输出：错误收集
     */
    private static void validateNoParentCycles(List<TaskImportModels.TaskItem> items,
                                               Map<String, Integer> aliasIndexById,
                                               List<String> errors) {
        int[] state = new int[items.size()];
        List<Integer> path = new ArrayList<>();
        for (int start = 0; start < items.size(); start++) {
            path.clear();
            int cursor = start;
            while (cursor >= 0) {
                if (state[cursor] == 2) {
                    break;
                }
                if (state[cursor] == 1) {
                    errors.add("#" + (cursor + 1) + " parentId: alias cycle detected");
                    break;
                }
                state[cursor] = 1;
                path.add(cursor);
                TaskImportModels.TaskItem item = items.get(cursor);
                String parentAlias = item == null ? null : normalizedOrNull(item.parentId);
                Integer next = parentAlias == null ? null : aliasIndexById.get(parentAlias);
                cursor = next == null ? -1 : next;
            }
            for (int visited : path) {
                state[visited] = 2;
            }
        }
    }

    /**
     * 统计批次文本总量（标题 + 描述 + 标签的 UTF-8 字节数）。
     *
     * <p>个人任务同步是单包全量发送，批次文本过大可能超出网络帧上限，
     * 因此在落库前用总量预算拒绝（null 条目按 0 计，错误已在别处报告）。
     *
     * @param items 任务项列表
     * @return UTF-8 字节总数
     */
    private static long batchTextBytes(List<TaskImportModels.TaskItem> items) {
        long total = 0L;
        for (TaskImportModels.TaskItem item : items) {
            if (item == null) {
                continue;
            }
            total += utf8Length(item.title);
            total += utf8Length(item.description);
            if (item.tags != null) {
                for (String tag : item.tags) {
                    total += utf8Length(tag);
                }
            }
        }
        return total;
    }

    /**
     * 返回文本的 UTF-8 字节长度，null 按 0 计。
     *
     * @param text 文本
     * @return UTF-8 字节长度
     */
    private static long utf8Length(String text) {
        return text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 校验触发器项：type 严格匹配枚举名、target 非空且不超 H2 列宽、targetCount ≥1。
     *
     * @param index   任务序号（1 基）
     * @param trigger 触发器项，可为 null（无触发器）
     * @param errors  输出：错误收集
     */
    private static void validateTriggerAt(int index, TaskImportModels.TriggerItem trigger, List<String> errors) {
        if (trigger == null) {
            return;
        }
        if (parseTriggerType(trigger.type) == null) {
            errors.add("#" + index + " trigger.type: expected one of " + triggerTypeNames() + " but got '"
                    + excerpt(trigger.type) + "'");
        }
        String target = normalizedOrNull(trigger.target);
        if (target == null) {
            errors.add("#" + index + " trigger.target: must not be blank");
        } else if (target.length() > MAX_TRIGGER_TARGET_LENGTH) {
            errors.add("#" + index + " trigger.target: length " + target.length()
                    + " exceeds limit " + MAX_TRIGGER_TARGET_LENGTH);
        }
        if (trigger.targetCount == null || trigger.targetCount < 1) {
            errors.add("#" + index + " trigger.targetCount: must be >= 1");
        }
    }

    /**
     * 截断用于错误消息回显的原始值：超长值只保留前段加省略号，防止刷屏。
     *
     * @param raw 原始值
     * @return 截断后的文本；null 返回空串
     */
    private static String excerpt(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() <= EXCERPT_LENGTH ? trimmed : trimmed.substring(0, EXCERPT_LENGTH) + "...";
    }

    /**
     * 第二遍扫描：把已通过校验的任务项转换为任务列表。
     *
     * <p>第一遍为每条任务建立骨架（标题、描述、优先级、标签、截止时间、触发器与归属信息），
     * 并登记别名 → 真实任务 ID 映射；第二遍按 parentId 挂接父子关系，
     * 同一父任务下的子任务按文档出现顺序分配 subtaskSortOrder（0 基递增）。
     *
     * @param items          任务项列表（已通过校验）
     * @param aliasIndexById 别名 → 任务序号映射
     * @param context        导入上下文
     * @return 任务列表，顺序与文档一致（父任务在前、子任务紧随其后）
     */
    private static List<Task> convert(List<TaskImportModels.TaskItem> items,
                                      Map<String, Integer> aliasIndexById,
                                      ImportContext context) {
        List<Task> tasks = new ArrayList<>(items.size());
        Map<String, Task> taskByAlias = new LinkedHashMap<>();
        for (TaskImportModels.TaskItem item : items) {
            Task task = new Task(normalizedOrNull(item.title), normalizedOrEmpty(item.description));
            Task.Priority priority = item.priority == null ? Task.Priority.MEDIUM : parsePriority(item.priority);
            task.setPriority(priority == null ? Task.Priority.MEDIUM : priority);
            applyTags(task, item.tags);
            if (item.dueDate != null) {
                task.setDueDate(item.dueDate);
            }
            if (item.trigger != null) {
                task.setTrigger(new TaskTrigger(
                        parseTriggerType(item.trigger.type),
                        normalizedOrNull(item.trigger.target),
                        item.trigger.targetCount == null ? 1 : item.trigger.targetCount));
            }
            task.setScope(Task.Scope.PERSONAL);
            if (context != null) {
                task.setCreatorUuid(context.creatorUuid());
                if (context.projectId() != null && !context.projectId().isEmpty()) {
                    task.setProjectId(context.projectId());
                }
            }
            tasks.add(task);
            String alias = normalizedOrNull(item.id);
            if (alias != null) {
                // 重复别名已在校验阶段拒绝，这里只登记首次出现
                taskByAlias.putIfAbsent(alias, task);
            }
        }
        Map<String, Long> nextSortOrderByParent = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            String parentId = normalizedOrNull(items.get(index).parentId);
            if (parentId == null) {
                continue;
            }
            Task parent = taskByAlias.get(parentId);
            if (parent == null) {
                continue;
            }
            Task child = tasks.get(index);
            child.setParentTaskId(parent.getId());
            long sortOrder = nextSortOrderByParent.getOrDefault(parent.getId(), 0L);
            nextSortOrderByParent.put(parent.getId(), sortOrder + 1L);
            child.setSubtaskSortOrder(sortOrder);
        }
        return tasks;
    }

    /**
     * 应用标签：去空白、去空串、去重并保持文档顺序。
     *
     * @param task 任务
     * @param tags 标签列表，可为 null
     */
    private static void applyTags(Task task, List<String> tags) {
        if (tags == null) {
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String trimmed = normalizedOrNull(tag);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        if (!normalized.isEmpty()) {
            task.setTags(normalized);
        }
    }

    /**
     * 严格解析优先级枚举名（大小写不敏感），非法值返回 null。
     *
     * @param raw 原始文本
     * @return 优先级；非法时为 null
     */
    private static Task.Priority parsePriority(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Task.Priority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 严格解析触发类型枚举名（大小写不敏感），非法值返回 null。
     *
     * <p>不复用 {@link TaskTrigger.Type#parse}：那个入口会把非法值静默回退为
     * ITEM_COLLECT，而导入必须显式报错。
     *
     * @param raw 原始文本
     * @return 触发类型；非法时为 null
     */
    private static TaskTrigger.Type parseTriggerType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return TaskTrigger.Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 拼接全部触发类型名（用于错误提示）。
     *
     * @return 形如 KILL_ENTITY / BREAK_BLOCK / ... 的文本
     */
    private static String triggerTypeNames() {
        return Arrays.toString(TaskTrigger.Type.values())
                .replace("[", "")
                .replace("]", "")
                .replace(",", " /");
    }

    /**
     * 截断错误明细并附总数提示，避免超长错误刷屏。
     *
     * @param errors 全部错误
     * @return 截断后的错误列表
     */
    private static List<String> summarizeErrors(List<String> errors) {
        if (errors.size() <= MAX_ERRORS_REPORTED) {
            return List.copyOf(errors);
        }
        List<String> summarized = new ArrayList<>(errors.subList(0, MAX_ERRORS_REPORTED));
        summarized.add("... and " + (errors.size() - MAX_ERRORS_REPORTED) + " more");
        return summarized;
    }

    /**
     * 构造文档级单条错误的失败结果。
     *
     * @param code  文档级错误码
     * @param error 错误明细
     * @return 失败结果
     */
    private static Result failure(String code, String error) {
        return new Result(List.of(), List.of(error), code);
    }

    /**
     * 文本规范化：null / 纯空白返回 null，其余返回 trim 后文本。
     *
     * @param raw 原始文本
     * @return 规范化文本；无效时为 null
     */
    private static String normalizedOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 文本规范化：null / 纯空白返回空串，其余返回 trim 后文本。
     *
     * @param raw 原始文本
     * @return 规范化文本
     */
    private static String normalizedOrEmpty(String raw) {
        String normalized = normalizedOrNull(raw);
        return normalized == null ? "" : normalized;
    }
}
