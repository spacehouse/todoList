package com.todolist.material;

import java.util.Map;
import java.util.Set;

/**
 * 材料反推的解析选项。
 *
 * @param maxDepth         递归深度上限（防止极端深树）
 * @param maxNodes         计划节点数上限（防止宽树爆炸导致界面卡顿，见设计文档 R1）
 * @param recipeOverrides  物品资源 ID → 指定配方 ID（覆盖默认选择策略，玩家在预览中切换）
 * @param stopAtItems      被玩家标记为「到此为止」的物品资源 ID 集合
 * @param forceExpandItems 被玩家要求「继续展开」的物品资源 ID 集合：
 *                         命中时即使其配方是熔炼/烧制类，也继续展开输入（D7 终止条件的反向干预），
 *                         同时也用于展开默认折叠的层级
 * @param defaultExpandDepth 默认展开到第几层：1 表示只展示根节点的直接材料，
 *                           更深层需要玩家逐节点「继续展开」
 * @param candidateOverrides 候选组（以该组默认代表物品的资源 ID 为键）→ 玩家选定的替代物品资源 ID；
 *                           一个输入可能有多个候选物品（物品标签/多选输入），玩家可在预览中指定用哪一个
 * @param preferredItems   玩家当前已有的物品资源 ID 集合（一般是背包内容）：
 *                         候选物品命中时优先选择，避免默认选中玩家手上没有的材料
 */
public record MaterialResolveOptions(int maxDepth,
                                     int maxNodes,
                                     Map<String, String> recipeOverrides,
                                     Set<String> stopAtItems,
                                     Set<String> forceExpandItems,
                                     int defaultExpandDepth,
                                     Map<String, String> candidateOverrides,
                                     Set<String> preferredItems) {

    /** 默认递归深度上限。 */
    public static final int DEFAULT_MAX_DEPTH = 32;
    /** 默认计划节点数上限。 */
    public static final int DEFAULT_MAX_NODES = 2000;
    /** 默认展开层数：只展开根物品的直接材料，更深的依赖由玩家按需展开。 */
    public static final int DEFAULT_EXPAND_DEPTH = 1;

    /**
     * 兼容旧调用形式的构造器，默认展开层数取 {@link #DEFAULT_EXPAND_DEPTH}，
     * 且不含候选物品覆盖与背包优先集合。
     */
    public MaterialResolveOptions(int maxDepth,
                                  int maxNodes,
                                  Map<String, String> recipeOverrides,
                                  Set<String> stopAtItems,
                                  Set<String> forceExpandItems) {
        this(maxDepth, maxNodes, recipeOverrides, stopAtItems, forceExpandItems, DEFAULT_EXPAND_DEPTH);
    }

    /**
     * 兼容旧调用形式的构造器：不含候选物品覆盖与背包优先集合。
     */
    public MaterialResolveOptions(int maxDepth,
                                  int maxNodes,
                                  Map<String, String> recipeOverrides,
                                  Set<String> stopAtItems,
                                  Set<String> forceExpandItems,
                                  int defaultExpandDepth) {
        this(maxDepth, maxNodes, recipeOverrides, stopAtItems, forceExpandItems, defaultExpandDepth, Map.of(), Set.of());
    }

    /**
     * 规范化上限值与集合，避免 null 与非法值扩散到反推逻辑。
     */
    public MaterialResolveOptions {
        maxDepth = Math.max(1, maxDepth);
        maxNodes = Math.max(1, maxNodes);
        recipeOverrides = recipeOverrides == null ? Map.of() : Map.copyOf(recipeOverrides);
        stopAtItems = stopAtItems == null ? Set.of() : Set.copyOf(stopAtItems);
        forceExpandItems = forceExpandItems == null ? Set.of() : Set.copyOf(forceExpandItems);
        defaultExpandDepth = Math.max(1, defaultExpandDepth);
        candidateOverrides = candidateOverrides == null ? Map.of() : Map.copyOf(candidateOverrides);
        preferredItems = preferredItems == null ? Set.of() : Set.copyOf(preferredItems);
    }

    /**
     * 返回默认选项。
     *
     * @return 默认选项
     */
    public static MaterialResolveOptions defaults() {
        return new MaterialResolveOptions(DEFAULT_MAX_DEPTH, DEFAULT_MAX_NODES, Map.of(), Set.of(), Set.of());
    }

    /**
     * 判断指定物品是否被玩家标记为「到此为止」。
     *
     * @param itemId 物品资源 ID
     * @return 命中时返回 true
     */
    public boolean isStoppedByUser(String itemId) {
        return itemId != null && stopAtItems.contains(itemId);
    }

    /**
     * 判断指定物品是否被玩家要求「继续展开」。
     *
     * @param itemId 物品资源 ID
     * @return 命中时返回 true
     */
    public boolean isForceExpanded(String itemId) {
        return itemId != null && forceExpandItems.contains(itemId);
    }

    /**
     * 读取指定物品的配方覆盖值。
     *
     * @param itemId 物品资源 ID
     * @return 指定配方 ID；未覆盖时返回 null
     */
    public String recipeOverride(String itemId) {
        return itemId == null ? null : recipeOverrides.get(itemId);
    }

    /**
     * 读取某个候选组的选定物品。
     *
     * @param candidateKey 候选组键（该组默认代表物品的资源 ID）
     * @return 玩家选定的物品资源 ID；未指定时返回 null
     */
    public String candidateOverride(String candidateKey) {
        return candidateKey == null ? null : candidateOverrides.get(candidateKey);
    }

    /**
     * 判断玩家当前是否已拥有该物品（用于候选物品的默认优先选择）。
     *
     * @param itemId 物品资源 ID
     * @return 已拥有时返回 true
     */
    public boolean isPreferredItem(String itemId) {
        return itemId != null && preferredItems.contains(itemId);
    }
}
