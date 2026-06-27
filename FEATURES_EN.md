# Features (v1.4.0)

TodoList is a Minecraft todo mod for single-player, LAN, and multiplayer collaboration across Fabric / Forge / NeoForge. This document describes what is available in v1.4.0 from a user perspective.

## Task Management

- Create, edit, delete, and complete/uncomplete tasks in an in-game GUI
- Task fields: title, description, priority (Low/Medium/High), and tags (comma-separated)
- Search & filters: real-time filtering by status, priority, text (title/description), and tags
- Unfinished tasks can be reordered directly with drag-and-drop
- Completed tasks now have a clearer split from open tasks, can be folded, and support one-click cleanup within the current project
- Both task deletion and completed-task cleanup now use explicit confirmation steps to reduce accidental operations
- Detail editing now supports an auto-save toggle; when enabled, edits can be saved on blur, task switch, project switch, quick add, and screen close

## Projects & Views

- Supports both personal projects and team projects
- Sidebar project list: search and switch projects; create/edit/delete projects
- Project search supports prefixes and dropdown hints for faster navigation in larger workspaces
- Team views (multiplayer): Unassigned, All Assigned, Assigned to Me
- Team projects support membership management, join requests, and team permission settings
- Starred projects are sorted first and can be reused as HUD sources
- Team projects can optionally enable an all-player task mode for broader shared workflows

## Multiplayer Collaboration & Permissions

- In multiplayer, personal tasks are persisted per player; team tasks are stored on the server and synchronized to players
- Team-view edits must be submitted via Save; Cancel or closing with Esc discards local unsaved edits and refreshes from the server to reduce conflicts
- A unified server-side Permission Center validates all team operations based on role, view scope, completion state, and assignment relationship
- The “Assign Others” dialog consistently uses project members, and offline members remain assignable
- Role differences:
  - Admins can fully manage team tasks, project members, and critical server commands
  - Regular players can claim/abandon tasks and complete tasks assigned to them; “Assign Others” is visible to admins only

## Command System

- Provides a unified `/todo` command entry and keeps `/todolist` as an alias
- Task commands cover list/query, pagination, quick add, add-with-project, done, remove, assign, claim, abandon, and batch cleanup with confirmation
- Project commands cover list, select, star, rename, create/remove projects, and member add/remove/role changes
- HUD commands cover visibility toggling
- Admin commands cover command-access mode, H2 status, online backup, health checks, TCP restart, database reload, and password reset

## HUD Display & Config

- The top-right HUD shows tasks for the current view, supports expand/collapse (H key), visibility toggle (J key), and count summaries
- The HUD header reflects the current view (Personal / Team-Unassigned / Team-All Assigned / Team-Assigned to Me)
- In true single-player worlds, the HUD default view is locked to Personal and team views are hidden
- Built-in HUD config screen: width, max height, todo/done item limits (0–30), default expanded state, show-when-empty, default view, and draggable preview positioning; supports Save & Apply
- If Mod Menu is installed, the HUD config screen can also be opened from the mod entry
- HUD semantics are aligned with the GUI for priority color blocks, hidden-count summaries, and current-view filtering; in H2 mode, high-frequency HUD paths can use database-backed query optimization

## H2 Storage & Maintenance

- The storage layer can switch between `NBT` and `H2`; `H2` mode can automatically migrate legacy `.dat` data
- H2 now covers tasks, projects, player project state, query optimization, backup, health checks, maintenance locking, and schema upgrades
- H2 supports external TCP access with separate `admin / readonly / readwrite` accounts
- Online backup, database reload, TCP restart, health checks, and password reset are available for maintenance workflows
- TCP lifecycle handling now invalidates stale reusable connections, reducing “storage unavailable” failures when moving between published LAN worlds and local single-player worlds

## Persistence & Localization

- Safer file-based persistence is used for tasks, projects, player project state, and config data
- When a primary data file is corrupted, the mod can recover automatically from the latest backup while preserving the corrupt copy
- Local singleplayer personal tasks keep local-file and per-player-file copies in sync to reduce accidental rollback-like restores
- Bilingual UI via language packs (English and Chinese), following the game language

## Audit Logs

- The server logs all effective team operations in a unified `[TEAM_OP]` format for auditing and troubleshooting

