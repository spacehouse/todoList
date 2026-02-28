package com.todolist.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages projects and provides CRUD operations
 */
public class ProjectManager {
    private final Map<String, Project> projects;
    private final List<ProjectChangeListener> listeners;

    public ProjectManager() {
        this.projects = new LinkedHashMap<>();
        this.listeners = new ArrayList<>();
    }

    /**
     * Add a new project
     */
    public void addProject(Project project) {
        projects.put(project.getId(), project);
        notifyListeners(ProjectChangeType.ADDED, project);
    }

    /**
     * Get project by ID
     */
    public Project getProject(String id) {
        return projects.get(id);
    }

    /**
     * Get all projects
     */
    public List<Project> getAllProjects() {
        return new ArrayList<>(projects.values());
    }

    /**
     * Get projects by scope
     */
    public List<Project> getProjectsByScope(Project.Scope scope) {
        List<Project> result = new ArrayList<>();
        for (Project project : projects.values()) {
            if (project.getScope() == scope) {
                result.add(project);
            }
        }
        return result;
    }

    /**
     * Update project
     */
    public void updateProject(Project project) {
        if (projects.containsKey(project.getId())) {
            projects.put(project.getId(), project);
            notifyListeners(ProjectChangeType.UPDATED, project);
        }
    }

    /**
     * Delete project
     */
    public void deleteProject(String projectId) {
        Project removed = projects.remove(projectId);
        if (removed != null) {
            notifyListeners(ProjectChangeType.REMOVED, removed);
        }
    }

    /**
     * Clear all projects
     */
    public void clearAll() {
        projects.clear();
        notifyListeners(ProjectChangeType.CLEARED, null);
    }

    // Listener Management

    public void addListener(ProjectChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProjectChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ProjectChangeType type, Project project) {
        for (ProjectChangeListener listener : listeners) {
            listener.onProjectChanged(type, project);
        }
    }

    // Inner classes and interfaces

    public enum ProjectChangeType {
        ADDED,
        UPDATED,
        REMOVED,
        CLEARED
    }

    public interface ProjectChangeListener {
        void onProjectChanged(ProjectChangeType type, Project project);
    }
}
