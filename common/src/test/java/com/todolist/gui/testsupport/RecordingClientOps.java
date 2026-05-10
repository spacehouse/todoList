package com.todolist.gui.testsupport;

import com.todolist.client.ClientBridge;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录型客户端桥接实现：用于断言 GUI 行为是否向平台层发起了正确调用。
 */
public final class RecordingClientOps implements ClientBridge.ClientOps {
    private final TaskManager teamTaskManager = new TaskManager();
    private final List<String> activeProjectSyncCalls = new ArrayList<>();
    private final List<Boolean> hudVisibilityCalls = new ArrayList<>();
    private final List<Project> addProjectCalls = new ArrayList<>();
    private final List<String> deleteProjectCalls = new ArrayList<>();
    private final List<Task> updateTaskCalls = new ArrayList<>();
    private final List<List<Task>> replaceAllTaskCalls = new ArrayList<>();
    private final List<List<Task>> replaceTeamTaskCalls = new ArrayList<>();
    private final List<String> joinProjectCalls = new ArrayList<>();
    private final List<Project> updateProjectCalls = new ArrayList<>();
    private final List<String> removeMemberCalls = new ArrayList<>();
    private final List<String> addMemberCalls = new ArrayList<>();
    private final List<String> updateMemberRoleCalls = new ArrayList<>();
    private final List<List<String>> hudStarredProjectIdCalls = new ArrayList<>();
    private String activeProjectId;
    private boolean hudVisible = true;
    private boolean teamProjectsEnabled = true;
    private boolean canSendUpdateMemberRole = true;
    private int requestTeamSyncCallCount;

    /**
     * 返回团队任务管理器，供界面读取本地团队任务缓存。
     *
     * @return 团队任务管理器
     */
    @Override
    public TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    /**
     * 返回当前测试环境是否启用了团队项目能力。
     *
     * @return true 表示启用团队项目能力
     */
    @Override
    public boolean isTeamProjectsEnabled() {
        return teamProjectsEnabled;
    }

    /**
     * 设置测试环境中的团队项目能力开关。
     *
     * @param enabled true 表示启用团队项目能力
     */
    public void setTeamProjectsEnabled(boolean enabled) {
        this.teamProjectsEnabled = enabled;
    }

    /**
     * 返回当前活动项目 ID。
     *
     * @return 当前活动项目 ID
     */
    @Override
    public String getActiveProjectId() {
        return activeProjectId;
    }

    /**
     * 设置当前活动项目 ID。
     *
     * @param projectId 项目 ID
     */
    @Override
    public void setActiveProjectId(String projectId) {
        this.activeProjectId = projectId;
    }

    /**
     * 记录同步活动项目 ID 的调用。
     *
     * @param projectId 项目 ID
     */
    @Override
    public void sendSetActiveProjectId(String projectId) {
        activeProjectSyncCalls.add(projectId);
    }

    /**
     * 返回当前 HUD 是否可见。
     *
     * @return true 表示 HUD 可见
     */
    @Override
    public boolean isHudVisible() {
        return hudVisible;
    }

    /**
     * 记录 HUD 可见性变更。
     *
     * @param visible 是否可见
     */
    @Override
    public void setHudVisible(boolean visible) {
        hudVisible = visible;
        hudVisibilityCalls.add(visible);
    }

    /**
     * 记录任务更新请求。
     *
     * @param task 更新后的任务
     */
    @Override
    public void sendUpdateTask(Task task) {
        updateTaskCalls.add(task);
    }

    /**
     * 记录个人任务整表替换请求。
     *
     * @param tasks 替换后的任务列表
     */
    @Override
    public void sendReplaceAllTasks(List<Task> tasks) {
        replaceAllTaskCalls.add(copyTasks(tasks));
    }

    /**
     * 记录团队任务整表替换请求。
     *
     * @param tasks 替换后的任务列表
     */
    @Override
    public void sendReplaceTeamTasks(List<Task> tasks) {
        replaceTeamTaskCalls.add(copyTasks(tasks));
    }

    /**
     * 记录团队任务合并请求，测试里沿用整表替换记录便于断言最终快照。
     *
     * @param baseTasks 保存发起时的团队任务基线
     * @param tasks 替换后的任务列表
     */
    @Override
    public void sendMergeTeamTasks(List<Task> baseTasks, List<Task> tasks) {
        sendReplaceTeamTasks(tasks);
    }

    /**
     * 记录团队任务重同步请求次数。
     */
    @Override
    public void requestTeamSync() {
        requestTeamSyncCallCount++;
    }

    /**
     * 记录加入项目请求。
     *
     * @param projectId 目标项目 ID
     */
    @Override
    public void sendRequestJoinProject(String projectId) {
        joinProjectCalls.add(projectId);
    }

    /**
     * 记录 HUD 星标项目同步请求。
     *
     * @param projectIds 星标项目 ID 列表
     */
    @Override
    public void sendHudStarredProjectIds(List<String> projectIds) {
        hudStarredProjectIdCalls.add(projectIds == null ? List.of() : List.copyOf(projectIds));
    }

    /**
     * 记录删除项目请求。
     *
     * @param projectId 目标项目 ID
     */
    @Override
    public void sendDeleteProject(String projectId) {
        deleteProjectCalls.add(projectId);
    }

    /**
     * 记录项目更新请求。
     *
     * @param project 更新后的项目
     */
    @Override
    public void sendUpdateProject(Project project) {
        updateProjectCalls.add(project);
    }

    /**
     * 记录移除项目成员请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     */
    @Override
    public void sendRemoveMember(String projectId, String memberUuid) {
        removeMemberCalls.add(projectId + ":" + memberUuid);
    }

    /**
     * 记录成员角色更新请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param role 成员角色
     */
    @Override
    public void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        updateMemberRoleCalls.add(projectId + ":" + memberUuid + ":" + role);
    }

    /**
     * 记录新增项目请求。
     *
     * @param project 新项目
     */
    @Override
    public void sendAddProject(Project project) {
        addProjectCalls.add(project);
    }

    /**
     * 记录新增成员请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param memberName 成员名称
     */
    @Override
    public void sendAddMember(String projectId, String memberUuid, String memberName) {
        addMemberCalls.add(projectId + ":" + memberUuid + ":" + memberName);
    }

    /**
     * 返回当前平台是否支持成员角色更新。
     *
     * @return true 表示支持
     */
    @Override
    public boolean canSendUpdateMemberRole() {
        return canSendUpdateMemberRole;
    }

    /**
     * 设置当前平台是否支持成员角色更新。
     *
     * @param enabled true 表示支持
     */
    public void setCanSendUpdateMemberRole(boolean enabled) {
        this.canSendUpdateMemberRole = enabled;
    }

    /**
     * 返回活动项目同步调用记录。
     *
     * @return 活动项目同步记录
     */
    public List<String> getActiveProjectSyncCalls() {
        return List.copyOf(activeProjectSyncCalls);
    }

    /**
     * 返回 HUD 可见性调用记录。
     *
     * @return HUD 可见性记录
     */
    public List<Boolean> getHudVisibilityCalls() {
        return List.copyOf(hudVisibilityCalls);
    }

    /**
     * 返回新增项目调用记录。
     *
     * @return 新增项目记录
     */
    public List<Project> getAddProjectCalls() {
        return List.copyOf(addProjectCalls);
    }

    /**
     * 返回删除项目调用记录。
     *
     * @return 删除项目记录
     */
    public List<String> getDeleteProjectCalls() {
        return List.copyOf(deleteProjectCalls);
    }

    /**
     * 返回任务更新调用记录。
     *
     * @return 任务更新记录
     */
    public List<Task> getUpdateTaskCalls() {
        return List.copyOf(updateTaskCalls);
    }

    /**
     * 返回个人任务整表替换记录。
     *
     * @return 个人任务整表替换记录
     */
    public List<List<Task>> getReplaceAllTaskCalls() {
        return List.copyOf(replaceAllTaskCalls);
    }

    /**
     * 返回团队任务整表替换记录。
     *
     * @return 团队任务整表替换记录
     */
    public List<List<Task>> getReplaceTeamTaskCalls() {
        return List.copyOf(replaceTeamTaskCalls);
    }

    /**
     * 返回请求团队同步的调用次数。
     *
     * @return 请求团队同步次数
     */
    public int getRequestTeamSyncCallCount() {
        return requestTeamSyncCallCount;
    }

    /**
     * 返回加入项目请求记录。
     *
     * @return 加入项目请求记录
     */
    public List<String> getJoinProjectCalls() {
        return List.copyOf(joinProjectCalls);
    }

    /**
     * 返回项目更新记录。
     *
     * @return 项目更新记录
     */
    public List<Project> getUpdateProjectCalls() {
        return List.copyOf(updateProjectCalls);
    }

    /**
     * 返回移除成员记录。
     *
     * @return 移除成员记录
     */
    public List<String> getRemoveMemberCalls() {
        return List.copyOf(removeMemberCalls);
    }

    /**
     * 返回新增成员记录。
     *
     * @return 新增成员记录
     */
    public List<String> getAddMemberCalls() {
        return List.copyOf(addMemberCalls);
    }

    /**
     * 返回成员角色更新记录。
     *
     * @return 成员角色更新记录
     */
    public List<String> getUpdateMemberRoleCalls() {
        return List.copyOf(updateMemberRoleCalls);
    }

    /**
     * 返回 HUD 星标项目同步记录。
     *
     * @return HUD 星标项目同步记录
     */
    public List<List<String>> getHudStarredProjectIdCalls() {
        return List.copyOf(hudStarredProjectIdCalls);
    }

    /**
     * 复制任务列表，避免断言前被外部再次修改。
     *
     * @param tasks 原始任务列表
     * @return 浅拷贝后的任务列表
     */
    private static List<Task> copyTasks(List<Task> tasks) {
        return tasks == null ? List.of() : new ArrayList<>(tasks);
    }
}
