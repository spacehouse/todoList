package com.todolist.gui;

/**
 * 任务详情编辑草稿，承载详情面板的临时输入状态。
 */
final class TaskDetailDraft {
    final String taskId;
    String title;
    String description;
    String tags;
    boolean titleEditing;

    /**
     * 创建详情草稿对象。
     *
     * @param taskId 任务 ID
     * @param title 任务标题
     * @param description 任务描述
     * @param tags 任务标签文本
     */
    TaskDetailDraft(String taskId, String title, String description, String tags) {
        this.taskId = taskId;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.tags = tags == null ? "" : tags;
        this.titleEditing = false;
    }
}
