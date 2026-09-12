package com.todolist.task;

import net.minecraft.nbt.CompoundTag;

/**
 * 任务触发器实体，描述"游戏内事件达成后自动完成任务"的判定条件。
 *
 * Features:
 * - 触发类型（击杀实体 / 破坏方块 / 合成物品 / 收集物品 / 获得进度）
 * - 目标资源 ID 与目标数量
 * - 当前进度（服务端事件驱动累加，随任务一起持久化）
 */
public class TaskTrigger {
    private static final String TYPE_KEY = "type";
    private static final String TARGET_KEY = "target";
    private static final String TARGET_COUNT_KEY = "targetCount";
    private static final String PROGRESS_KEY = "progress";

    private final Type type;
    private final String target;
    private final int targetCount;
    private int progress;

    /**
     * 创建任务触发器。
     *
     * @param type        触发类型
     * @param target      目标资源 ID，如 minecraft:zombie
     * @param targetCount 目标数量，最小为 1
     */
    public TaskTrigger(Type type, String target, int targetCount) {
        this.type = type == null ? Type.ITEM_COLLECT : type;
        this.target = normalizeTarget(target);
        this.targetCount = Math.max(1, targetCount);
        this.progress = 0;
    }

    private TaskTrigger(Type type, String target, int targetCount, int progress) {
        this.type = type;
        this.target = target;
        this.targetCount = targetCount;
        this.progress = clampProgress(progress);
    }

    /**
     * 返回触发类型。
     *
     * @return 触发类型
     */
    public Type getType() {
        return type;
    }

    /**
     * 返回目标资源 ID。
     *
     * @return 目标资源 ID
     */
    public String getTarget() {
        return target;
    }

    /**
     * 返回目标数量。
     *
     * @return 目标数量
     */
    public int getTargetCount() {
        return targetCount;
    }

    /**
     * 返回当前进度。
     *
     * @return 当前进度，不超过目标数量
     */
    public int getProgress() {
        return progress;
    }

    /**
     * 设置当前进度并收敛到有效区间。
     *
     * @param progress 新进度
     */
    public void setProgress(int progress) {
        this.progress = clampProgress(progress);
    }

    /**
     * 累加进度并返回是否已达标。
     *
     * @param amount 本次事件增量
     * @return 进度达到目标数量时返回 true
     */
    public boolean addProgress(int amount) {
        this.progress = clampProgress(this.progress + amount);
        return isSatisfied();
    }

    /**
     * 判断进度是否已达标。
     *
     * @return 达标时返回 true
     */
    public boolean isSatisfied() {
        return progress >= targetCount;
    }

    /**
     * 将触发器写入 NBT，作为网络传输与 H2 行映射的中间格式。
     *
     * @return 触发器 NBT 表示
     */
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(TYPE_KEY, type.name());
        nbt.putString(TARGET_KEY, target);
        nbt.putInt(TARGET_COUNT_KEY, targetCount);
        nbt.putInt(PROGRESS_KEY, progress);
        return nbt;
    }

    /**
     * 从 NBT 还原触发器，字段缺失或非法时按默认值降级。
     *
     * @param nbt 触发器 NBT
     * @return 还原后的触发器
     */
    public static TaskTrigger fromNbt(CompoundTag nbt) {
        Type type = Type.parse(nbt.getString(TYPE_KEY));
        String target = normalizeTarget(nbt.getString(TARGET_KEY));
        int targetCount = Math.max(1, nbt.getInt(TARGET_COUNT_KEY));
        int progress = nbt.getInt(PROGRESS_KEY);
        return new TaskTrigger(type, target, targetCount, progress);
    }

    /**
     * 收敛进度到 [0, targetCount] 区间。
     *
     * @param progress 原始进度
     * @return 收敛后的进度
     */
    private int clampProgress(int progress) {
        return Math.max(0, Math.min(targetCount, progress));
    }

    /**
     * 标准化目标资源 ID，空白值回退为空串（无效标记，不参与事件匹配）。
     *
     * @param target 原始目标
     * @return 标准化后的目标
     */
    private static String normalizeTarget(String target) {
        if (target == null) {
            return "";
        }
        return target.trim();
    }

    /**
     * 判断触发器是否具备参与事件匹配的有效条件。
     *
     * @return 类型与目标均有效时返回 true
     */
    public boolean isValid() {
        return type != null && target != null && !target.isEmpty() && targetCount > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskTrigger that = (TaskTrigger) o;
        return type == that.type
                && targetCount == that.targetCount
                && progress == that.progress
                && java.util.Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, target, targetCount, progress);
    }

    @Override
    public String toString() {
        return "TaskTrigger{"
                + "type=" + type
                + ", target='" + target + '\''
                + ", targetCount=" + targetCount
                + ", progress=" + progress
                + '}';
    }

    /**
     * 内置触发类型，v1 覆盖五种高频游戏事件。
     */
    public enum Type {
        /** 击杀指定实体 */
        KILL_ENTITY,
        /** 破坏指定方块 */
        BREAK_BLOCK,
        /** 合成指定物品 */
        CRAFT_ITEM,
        /** 持有指定物品数量达标 */
        ITEM_COLLECT,
        /** 获得指定进度 */
        ADVANCEMENT;

        /**
         * 解析触发类型文本，非法值回退为 ITEM_COLLECT。
         *
         * @param name 类型名
         * @return 解析后的类型
         */
        public static Type parse(String name) {
            if (name == null || name.isEmpty()) {
                return ITEM_COLLECT;
            }
            try {
                return Type.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return ITEM_COLLECT;
            }
        }
    }
}
