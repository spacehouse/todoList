package com.todolist.gui;

import com.todolist.project.Project;

/**
 * TodoScreen 静态界面状态快照，用于跨界面恢复筛选和视图状态。
 */
final class LastGuiState {
    Project.Scope projectScopeFilter;
    String currentProjectId;
    ViewMode viewMode;
    SpaceMode spaceMode;
    TaskViewOption taskViewOption;
    boolean activeExpanded = true;
    boolean completedExpanded;
    int currentPriorityFilter;
    String searchQuery;
    String projectSearchQuery;
    String lastPersonalProjectId;
    String lastTeamProjectId;
}

