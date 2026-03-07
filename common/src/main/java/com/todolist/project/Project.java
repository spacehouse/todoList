package com.todolist.project;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Project entity for Todo List
 *
 * Represents a collection of tasks with specific settings and permissions.
 */
public class Project {
    private static final int NBT_COMPOUND_TYPE = 10;
    private String id;
    private String name;
    private int color; // Hex color for UI representation
    private Scope scope;
    private String ownerUuid;
    private long createdAt;
    
    // Permission settings
    private boolean allowMemberCreate = false; // Whether members can create tasks
    
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
        if ("default-personal-project".equals(this.id)) {
            return true;
        }
        return matchesAnyName(this.name,
                "gui.todolist.project.default.personal",
                "默认项目",
                "Default Project",
                "Inbox");
    }

    public boolean isDefaultTeamProject() {
        if ("default-team-project".equals(this.id)) {
            return true;
        }
        return matchesAnyName(this.name,
                "gui.todolist.project.default.team",
                "团队项目",
                "Team Project",
                "General");
    }

    private static boolean matchesAnyName(String value, String... candidates) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
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

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", id);
        if (name != null) nbt.putString("name", name);
        nbt.putInt("color", color);
        nbt.putString("scope", scope.name());
        if (ownerUuid != null) nbt.putString("ownerUuid", ownerUuid);
        nbt.putLong("createdAt", createdAt);
        nbt.putBoolean("allowMemberCreate", allowMemberCreate);
        
        ListTag memberList = new ListTag();
        for (Map.Entry<String, ProjectRole> entry : members.entrySet()) {
            CompoundTag memberTag = new CompoundTag();
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

    public static Project fromNbt(CompoundTag nbt) {
        Project project = new Project();
        if (nbt.contains("id")) {
            String id = nbt.getString("id");
            if (id != null) {
                id = id.trim();
            }
            if (id != null && !id.isEmpty()) {
                project.setId(id);
            }
        }
        if (nbt.contains("name")) project.setName(nbt.getString("name"));
        if (nbt.contains("color")) project.setColor(nbt.getInt("color"));
        if (nbt.contains("scope")) {
            String scope = nbt.getString("scope");
            if (scope != null) {
                scope = scope.trim();
            }
            try {
                project.setScope(Scope.valueOf(scope));
            } catch (IllegalArgumentException e) {
                project.setScope(Scope.PERSONAL);
            }
        }
        if (nbt.contains("ownerUuid")) project.setOwnerUuid(nbt.getString("ownerUuid"));
        if (nbt.contains("createdAt")) project.setCreatedAt(nbt.getLong("createdAt"));
        if (nbt.contains("allowMemberCreate")) project.setAllowMemberCreate(nbt.getBoolean("allowMemberCreate"));
        
        if (nbt.contains("members")) {
            ListTag memberList = nbt.getList("members", NBT_COMPOUND_TYPE);
            for (int i = 0; i < memberList.size(); i++) {
                CompoundTag memberTag = memberList.getCompound(i);
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
