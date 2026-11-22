package com.todolist.importer;

import java.util.List;

/**
 * 任务导入格式 v1 的数据载体（DTO），只承载 Gson 反序列化结果，不做任何校验。
 *
 * <p>字段一律保留 JSON 原始值（可为 null / 非法值），合法性判定统一在
 * {@link TaskImporter} 中完成：导入必须严格校验并收集全部错误，不能静默降级
 * （与运行期 {@code TaskTrigger.Type.parse} 的容错语义不同，见设计文档）。
 * 未知字段由 Gson 默认忽略，为格式前向兼容保留空间。
 *
 * <p>格式规范与设计决策见 {@code docs/feat-task-import-format.md}。
 */
public final class TaskImportModels {

    /**
     * 工具类不需要实例化。
     */
    private TaskImportModels() {
    }

    /**
     * 导入文档根对象：{@code {"formatVersion": 1, "tasks": [...]}}。
     */
    public static final class Document {
        /** 格式版本号，当前仅支持 1 */
        public Integer formatVersion;
        /** 任务项列表 */
        public List<TaskItem> tasks;
    }

    /**
     * 单条任务项。{@code id} / {@code parentId} 是文档内局部别名，
     * 用于在导入时建立父子关系，不落库（落库使用 Task 自身的真实 ID）。
     */
    public static final class TaskItem {
        /** 本地别名，可选；供其他任务的 parentId 引用 */
        public String id;
        /** 任务标题，必填非空；支持 [物品ID] 标题 markup */
        public String title;
        /** 任务描述，可选 */
        public String description;
        /** 优先级 LOW / MEDIUM / HIGH，可选，默认 MEDIUM，大小写不敏感 */
        public String priority;
        /** 标签列表，可选；导入时去空去重 */
        public List<String> tags;
        /** 截止时间，可选；epoch 毫秒 */
        public Long dueDate;
        /** 父任务别名，可选；必须引用同文档中已声明（含后文声明）的 id */
        public String parentId;
        /** 触发器，可选 */
        public TriggerItem trigger;
    }

    /**
     * 触发器项。{@code progress} 不允许导入（恒为 0），进度只能由游戏内事件推进。
     */
    public static final class TriggerItem {
        /** 触发类型，严格匹配 {@link com.todolist.task.TaskTrigger.Type} 的枚举名 */
        public String type;
        /** 目标资源 ID，如 minecraft:iron_ingot */
        public String target;
        /** 目标数量，≥1 */
        public Integer targetCount;
    }
}
