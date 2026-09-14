package com.todolist.material;

/**
 * 材料反推使用的配方类型分类。
 *
 * <p>{@link #priority()} 用于多配方并列时的稳定排序（数值越小越优先）；
 * {@link #isCooking()} 用于终止条件判定：熔炼/烧制类配方的输入视为世界可获取的基础材料，
 * 不再继续往上反推（避免把铁锭推成铁粒）。
 */
public enum MaterialRecipeKind {
    /** 工作台/背包合成（有序或无序）。 */
    CRAFTING(0, false),
    /** 切石机。 */
    STONECUTTING(1, false),
    /** 锻造台。 */
    SMITHING(2, false),
    /** 熔炉熔炼。 */
    SMELTING(3, true),
    /** 高炉熔炼。 */
    BLASTING(4, true),
    /** 烟熏炉。 */
    SMOKING(5, true),
    /** 营火烹饪。 */
    CAMPFIRE(6, true),
    /** 其他/未识别的配方类型。 */
    OTHER(7, false);

    private final int priority;
    private final boolean cooking;

    MaterialRecipeKind(int priority, boolean cooking) {
        this.priority = priority;
        this.cooking = cooking;
    }

    /**
     * 返回并列排序优先级，数值越小越优先。
     *
     * @return 优先级
     */
    public int priority() {
        return priority;
    }

    /**
     * 判断是否为熔炼/烧制类配方（终止条件的判定依据）。
     *
     * @return 熔炼类返回 true
     */
    public boolean isCooking() {
        return cooking;
    }
}
