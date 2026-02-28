package com.todolist.project;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Project entity for Todo List
 *
 * Represents a collection of tasks with specific settings and permissions.
 */
public class Project {
    private String id;
    private String name;
    private int color; // Hex color for UI representation
    private Scope scope;
    private String ownerUuid;
    private long createdAt;
    
    // Permission settings
    private boolean allowMemberCreate = true; // Whether members can create tasks
    
    // Member roles: UUID -> Role
    private Map<String, ProjectRole> members = new HashMap<>();
    private Map<String, String> memberNames = new HashMap<>();

    public Project() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.color = 0xFFFFFF; // Default white
        this.scope = Scope.PERSONAL;
    }

    public Project(String name, Scope scope, String ownerUuid) {
        this();
        this.name = name;
        this.scope = scope;
        this.ownerUuid = ownerUuid;
        // Owner is automatically added as a member with OWNER role
        if (ownerUuid != null) {
            this.members.put(ownerUuid, ProjectRole.PROJECT_MANAGER);
        }
    }

    public enum Scope {
        PERSONAL,
        TEAM
    }

    public enum ProjectRole {
        PROJECT_MANAGER(3),
        LEAD(2),
        MEMBER(1);

        private final int level;

        ProjectRole(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
        
        public boolean atLeast(ProjectRole other) {
            return this.level >= other.level;
        }
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefaultPersonalProject() {
        return "gui.todolist.project.default.personal".equals(this.name);
    }

    public boolean isDefaultTeamProject() {
        return "gui.todolist.project.default.team".equals(this.name);
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAllowMemberCreate() {
        return allowMemberCreate;
    }

    public void setAllowMemberCreate(boolean allowMemberCreate) {
        this.allowMemberCreate = allowMemberCreate;
    }

    public Map<String, ProjectRole> getMembers() {
        return members;
    }

    public void setMembers(Map<String, ProjectRole> members) {
        this.members = members;
    }

    public Map<String, String> getMemberNames() {
        return memberNames;
    }

    public void setMemberNames(Map<String, String> memberNames) {
        this.memberNames = memberNames;
    }
    
    public void addMember(String uuid, ProjectRole role) {
        this.members.put(uuid, role);
    }

    public void addMember(String uuid, ProjectRole role, String name) {
        this.members.put(uuid, role);
        if (name != null && !name.isEmpty()) {
            this.memberNames.put(uuid, name);
        }
    }
    
    public void removeMember(String uuid) {
        this.members.remove(uuid);
        this.memberNames.remove(uuid);
    }
    
    public ProjectRole getMemberRole(String uuid) {
        return this.members.get(uuid);
    }

    public String getMemberName(String uuid) {
        return this.memberNames.get(uuid);
    }

    public void setMemberName(String uuid, String name) {
        if (uuid == null || uuid.isEmpty()) return;
        if (name == null || name.isEmpty()) return;
        this.memberNames.put(uuid, name);
    }

    // NBT Serialization

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", id);
        if (name != null) nbt.putString("name", name);
        nbt.putInt("color", color);
        nbt.putString("scope", scope.name());
        if (ownerUuid != null) nbt.putString("ownerUuid", ownerUuid);
        nbt.putLong("createdAt", createdAt);
        nbt.putBoolean("allowMemberCreate", allowMemberCreate);
        
        NbtList memberList = new NbtList();
        for (Map.Entry<String, ProjectRole> entry : members.entrySet()) {
            NbtCompound memberTag = new NbtCompound();
            memberTag.putString("uuid", entry.getKey());
            memberTag.putString("role", entry.getValue().name());
            String name = memberNames.get(entry.getKey());
            if (name != null && !name.isEmpty()) {
                memberTag.putString("name", name);
            }
            memberList.add(memberTag);
        }
        nbt.put("members", memberList);
        
        return nbt;
    }

    public static Project fromNbt(NbtCompound nbt) {
        Project project = new Project();
        if (nbt.contains("id")) project.setId(nbt.getString("id"));
        if (nbt.contains("name")) project.setName(nbt.getString("name"));
        if (nbt.contains("color")) project.setColor(nbt.getInt("color"));
        if (nbt.contains("scope")) project.setScope(Scope.valueOf(nbt.getString("scope")));
        if (nbt.contains("ownerUuid")) project.setOwnerUuid(nbt.getString("ownerUuid"));
        if (nbt.contains("createdAt")) project.setCreatedAt(nbt.getLong("createdAt"));
        if (nbt.contains("allowMemberCreate")) project.setAllowMemberCreate(nbt.getBoolean("allowMemberCreate"));
        
        if (nbt.contains("members")) {
            NbtList memberList = nbt.getList("members", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < memberList.size(); i++) {
                NbtCompound memberTag = memberList.getCompound(i);
                String uuid = memberTag.getString("uuid");
                String roleStr = memberTag.getString("role");
                ProjectRole role;
                if ("OWNER".equals(roleStr) || "PROJECT_MANAGER".equals(roleStr)) {
                    role = ProjectRole.PROJECT_MANAGER;
                } else if ("ADMIN".equals(roleStr) || "LEAD".equals(roleStr)) {
                    role = ProjectRole.LEAD;
                } else {
                    role = ProjectRole.MEMBER;
                }
                project.addMember(uuid, role);
                if (memberTag.contains("name")) {
                    String name = memberTag.getString("name");
                    if (name != null && !name.isEmpty()) {
                        project.setMemberName(uuid, name);
                    }
                }
            }
        }
        
        return project;
    }
}
