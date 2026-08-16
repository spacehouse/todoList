# Changelog (English)

All notable changes to this project will be documented in this file.

Date format: `YYYY-MM-DD`

## [1.4.1] - 2026-08-16

### New Features
- **Subtasks are now supported**: Tasks can now contain subtasks, making it much easier to break larger goals into smaller, manageable steps.
- **Parent-task actions are more convenient**: From a parent task, you can now complete or uncomplete its direct subtasks in one step, and in team projects you can also claim or assign direct subtasks that have not been assigned yet.

### Improvements & Fixes
- **H2 is now the default storage path**: The mod now uses H2 as the normal storage mode going forward instead of continuing day-to-day support for legacy NBT storage; existing old NBT data is still migrated automatically on first access.
- **Clearer in-game H2 guidance**: Players with management access now get clearer hints for common H2 commands such as checking status, creating backups, reloading the database, and running health checks.

## [1.4.0] - 2026-06-17

### New Features
- **H2 Storage Backend Delivered**: Added `storageBackend=h2` as a relational storage mode covering tasks, projects, player project state, legacy `.dat` migration, and SQL-backed GUI/HUD query paths.
- **H2 TCP Access & Maintenance Commands**: Added H2 TCP configuration, three account roles (`admin / readonly / readwrite`), online backup, health checks, database reload, TCP restart, and password reset for local or LAN maintenance workflows.
- **Expanded Command System**: `/todo` now covers task operations, project management, member management, HUD control, join approval, command-access mode switching, and H2 maintenance entry points, while keeping `/todolist` as an alias.
- **One-click Cleanup for Completed Tasks**: Added a current-project cleanup action in the GUI completed section, protected by a second confirmation step.
- **GUI Auto-save Toggle**: Added a GUI editing auto-save toggle in the config screen, allowing detail edits to be saved on blur, task switch, project switch, and screen close.

### Improvements & Fixes
- **HUD / GUI Query Optimization**: In H2 mode, project counts, HUD lists, and advanced GUI filters now increasingly use database queries instead of repeated full in-memory scans.
- **H2 Lifecycle Stability Fixes**: Fixed stale reusable connections being reused across LAN publish/exit/back-to-local transitions, reducing false “storage unavailable” failures.
- **H2 Write-path Reliability Fixes**: Fixed H2 lock waits being misclassified as storage-unavailable, restored personal-task save performance, and reduced stutter when switching back to personal projects after team saves.
- **HUD & Input Experience Fixes**: Fixed HUD quick-toggle flicker, improved expanded-state summary text, and clarified description/tag input placeholder behavior.
- **Task Interaction Stability Fixes**: Fixed async-save versus team-sync races, fast-operation flashing in team tasks, and delayed GUI/HUD refresh after synchronization.
- **Config Guidance Improvements**: Added bilingual explanatory comments for `storageBackend` and `h2BackupOnStart` to reduce configuration mistakes.

### Documentation & Operations
- **H2 Documentation Added**: Added dedicated guides for storage-mode switching and external-client access, covering `NBT/H2` switching, TCP access, account usage, and common troubleshooting.

## [1.3.0] - 2026-04-26

### New Features
- **Main Todo GUI Overhaul**: Reworked the main layout, project sidebar, task list, and task-detail drawer with clearer view switching and more responsive space allocation.
- **Drag Sorting for Open Tasks**: Added direct drag-and-drop reordering for unfinished tasks so personal and team backlogs can be reprioritized in-game.
- **Segmented Task List & Delete Confirmation**: Introduced a clearer open/done split with completed-section folding baseline, plus a dedicated delete confirmation flow to reduce accidental removals.
- **Team Project Search & Member Interaction Upgrade**: Added project search prefixes with dropdown hints and unified the member-selection / assignment dialog layout for faster multiplayer workflows.
- **Team All-player Mode**: Added an optional all-player mode for team projects, giving team tasks a more consistent collaboration model in shared environments.

### Improvements & Fixes
- **HUD / View Consistency**: Synchronized HUD priority color blocks, hidden-count semantics, and team-view display logic so the HUD follows the main GUI more consistently.
- **Team Interaction Feedback**: Improved feedback for claim, abandon, assign, and team-all-view interactions to reduce cases where actions succeed but the GUI feels stale.
- **Immediate Completion Persistence**: Fixed task completion toggles not being persisted quickly enough, reducing rollback-like behavior after refreshes or screen close.
- **Drag-sort Input Fix**: Fixed drag sorting occasionally requiring an extra click before it actually took effect.
- **Singleplayer Personal-task Restore Fix**: Fixed local singleplayer personal-task rollback/clearing caused by drift between the local task file and the per-player task file.

### Stability & Testing
- **Safer Persistence & Recovery**: Task, project, player-project-state, and config persistence now use temp writes, backup recovery, and corrupt-file preservation for much safer crash / power-loss handling.
- **Two-phase Project Reload**: Project reload now uses a two-phase flow so failed reads do not clear the in-memory project state before recovery completes.
- **Regression Coverage Expansion**: Added persistence safety regression tests and local-singleplayer dual-copy synchronization tests to improve release confidence.

## [1.1.4] - 2026-03-29

### New Features
- **Local Personal-task Isolation**: Refactored personal-task storage and HUD layout so local singleplayer data is isolated more cleanly across different runtime contexts.
- **HUD Visibility Sync**: Added more complete HUD visibility synchronization and improved coordination between the HUD and the main GUI view state.
- **LAN Team Sync Improvements**: Improved the team-data synchronization flow in published LAN worlds to make team projects behave more reliably and consistently.

### Improvements & Fixes
- **Cross-project Save Fix**: Fixed incorrect save behavior when switching across projects, preventing accidental overwrite or missing persisted changes.
- **LAN Personal-task Restore Fix**: Fixed incorrect personal-task restore behavior in LAN sessions to improve stability when moving between local and multiplayer contexts.
- **Assign Dialog Member Source Fix**: Fixed the "Assign Others" dialog showing online players instead of current project members; offline members can now still be displayed and assigned.
- **Assign Dialog Layout Fix**: Improved adaptive layout in the "Assign Others" dialog so the cancel button stays visible in small windows or large GUI scales.

### Testing & Stability
- **Offline Self-test Coverage**: Added offline self-test support for GUI components and the command system to improve regression verification.
- **Integration Test Entry Expansion**: Added a dedicated command-system test entry point for more targeted integration testing and troubleshooting.

## [1.1.3] - 2026-03-21

### Improvements & Fixes
- **LAN Team Availability (Fabric/Forge)**: Team projects now become available immediately after the host publishes a LAN world, even before a second player joins.
- **Singleplayer Safety Preserved**: In pure local singleplayer (LAN not published), team projects remain disabled to keep previous singleplayer behavior unchanged.
- **Unified Server-side Rule**: Team project create/select/visibility/active-project sync now consistently follow `!dedicated && !published` as the singleplayer-disabled rule.
- **Forge Host Networking Fix**: On Forge, local host packet capability checks no longer hard-fail in LAN mode, preventing host-side fallback behavior that blocked team project workflow.

## [1.1.2] - 2026-03-17

### Improvements & Fixes
- **[forge] Key Binding Fix**: Fixed Forge key mappings not being registered on the mod event bus. `K/H/J` now appear correctly in Controls and can be remapped.
- **Config UI Enhancement**: Added a new **HUD Visibility** toggle in the HUD config row, placed to the right of **Show HUD when empty**.
- **Input Consistency**: Unified key trigger behavior with `consumeClick()`, keeping repeat-trigger behavior consistent while holding a key.
- **Save Semantics Consistency (Forge/Fabric)**: Unified personal-task persistence so changes are written only when clicking **Save**; closing the screen now discards unsaved personal changes, fixing the previous Forge-only “auto-saved” behavior mismatch.
- **HUD Rendering Performance**: Refactored the HUD render pipeline with throttled model refresh and row layout caching to reduce per-frame allocations and text layout cost.
- **Forge Personal Sync Guard**: When local personal-task changes are unsaved, Forge client now skips writing incoming personal sync packets to local storage, preventing unsaved edits from being overwritten or appearing auto-applied.

## [1.1.1] - 2026-03-09

### Improvements & Fixes
- **Performance**: Significantly improved HUD rendering performance by reducing object creation and removing redundant render calls, addressing major FPS drops when HUD is enabled (especially on Forge).
- **Fabric Fix**: Further optimized HUD render performance on Fabric.

## [1.0.0] - 2026-02-21

### Capability Overview
- **Task Management**: In-game GUI to create/edit/delete/complete tasks; priorities (Low/Medium/High), tags, and search & filters (status/priority/text/tags).
- **Projects**: Personal and team projects; sidebar project list with search and switching; create/edit/delete projects, with team membership management and join requests.
- **HUD Display & Config**: Top-right HUD list for the current view with expand/collapse and count display; in true single-player, HUD is locked to Personal and hides team views; HUD config screen covers size, item limits, default view, show-when-empty, and draggable preview positioning.
- **Multiplayer Collaboration**: Per-player personal tasks persisted by player UUID; server-shared team tasks synchronized to players; team edits are submitted via Save, while Cancel/Esc discards local changes and refreshes from the server.
- **Assignment Workflow**: Team tasks support claim/abandon/assign and an “Assigned to Me” view; assignee names can fall back for offline players to keep labels readable.
- **Permissions & Audit**: A unified server-side Permission Center checks team operations based on role, view scope, completion state, and assignment; all effective team operations are logged in a unified `[TEAM_OP]` format.
- **Persistence & Localization**: File-based persistence for tasks and projects (single-player and multiplayer); bilingual UI/docs (ZH/EN).
