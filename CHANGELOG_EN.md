# Changelog (English)

All notable changes to this project will be documented in this file.

Date format: `YYYY-MM-DD`

## [1.0.0] - 2026-02-18

### Added/Changed – HUD & Config
- In true single-player worlds, the HUD default list view is forced to **Personal**. The corresponding option in the HUD config screen is locked to Personal and cannot be cycled.
- In single-player worlds, HUD no longer shows any team views to avoid confusion such as “seeing team tasks while playing solo”.
- The HUD header now displays the current view name (Personal / Team-Unassigned / Team-All Assigned / Team-Assigned to Me), matching the GUI view buttons.
- HUD team views now strictly follow the same filtering semantics as the main GUI:
  - Team-Unassigned: only team tasks with no assignee.
  - Team-All Assigned: only team tasks that have been assigned to any player.
  - Team-Assigned to Me: only team tasks whose assignee is the current player.

### Added/Changed – Team Tasks & Permissions
- Introduced a unified audit log for team task operations:
  - On the server, when handling team saves, completion toggles, claim/abandon, and assign-others operations, each effective action is validated by the Permission Center and logged using a unified `[TEAM_OP]` format (including player, operation type, task ID, title and change details).
- Enhanced `TEAM_REPLACE_TASKS` (save team tasks) logic:
  - The server compares each incoming task with the current stored task, and uses the Permission Center to decide whether completion status and assignee changes are allowed.
  - Only permitted changes are applied and logged. Denied changes are ignored, and the player receives a hint that the client view has been refreshed from server state (in case of conflicts or lack of permission).
- Enhanced single-task team operations:
  - `TEAM_TOGGLE_TASK_ID`: the server computes context based on the current view and assignee, checks `TOGGLE_COMPLETE` via the Permission Center, and logs the operation with before/after completion state.
  - `TEAM_ASSIGN_TASK_ID`: supports three semantics—claim, abandon, and assign others—unified through the Permission Center. Successful changes are logged with before/after assignee details.

### Added/Changed – Team View Sync & Cancel Logic
- Added `TEAM_REQUEST_SYNC` packet:
  - The client can actively request the latest team task list from the server.
- Unified behavior in the team task GUI:
  - In team views, when clicking **Save**:
    - Client sends `TEAM_REPLACE_TASKS` with local changes to the server.
    - After the save returns and the screen closes, `TEAM_REQUEST_SYNC` is sent so the client re-fetches the final team task list from the server, ensuring the GUI matches the actual stored state.
  - In team views, when clicking **Cancel** or closing with **Esc**:
    - Local changes are not submitted.
    - `TEAM_REQUEST_SYNC` is sent to discard unsaved local edits and reload the latest state from the server.
  - When closing team views, the flags `hasUnsavedChanges` and `teamHasUnsavedChanges` are cleared.

### Added/Changed – GUI UX Improvements
- Reset selected task on view switch:
  - When switching between views in the main todo GUI, `selectedTask` is cleared so that tasks from the previous view are not unintentionally modified by change events from empty input fields in the new view (e.g. task title becoming empty).
- Priority button behavior:
  - When no task is selected, priority buttons are still enabled as long as the **Add** button is active, so players can set the default priority for the next new task.
  - When a task is selected and the player has edit permission and the task is not completed, clicking a priority button updates the task’s priority immediately.
- Team assignment buttons visibility:
  - Regular players in team views only see **Claim** and **Abandon** buttons.
  - The **Assign Others** button is only visible for admins (clients with OP permission), and still requires the Permission Center to allow `ASSIGN_OTHERS` before it can be used.

### Added/Changed – HUD Task Refresh
- HUD task refresh logic improvements:
  - Kept the periodic refresh from local storage for personal tasks.
  - After manual save in the GUI, the HUD renderer’s `forceRefreshTasks` API is called to refresh HUD data immediately, reducing delay between GUI changes and HUD display.

### Added/Changed – Documentation
- Updated `README.md`:
  - Expanded the description of HUD behavior in single-player: default view locked to Personal, team HUD views hidden.
  - Documented the precise semantics of team views (Unassigned / All Assigned / Assigned to Me) both in GUI and HUD.
  - Clarified that in team views, after Save, Cancel or closing with Esc, the client re-syncs team tasks from the server, ensuring the list always matches server state.
  - Introduced a dedicated “Permission Center” subsection under the permission system, describing how it evaluates operations based on role, view scope, completion state and assignment.
  - Promoted “Operation Logs” to a standalone section, explaining that all Permission-Center-approved team operations are recorded using a unified `[TEAM_OP]` format in server logs.
- Updated `FEATURES_TODO.md`:
  - Marked “Multiplayer Server Support” as “partially implemented”, noting that v1.0.0 already provides per-player tasks, shared team tasks, permission management and basic sync.
  - Updated the “last updated” date to `2026-02-18`.
- Updated and added other docs:
  - Updated `FEATURES_COMPLETED.md` to reflect the finalized behavior for single-player HUD locking, team HUD views, team sync on Save/Cancel/Esc, and the split between Permission Center and Operation Logs.
  - Added `FEATURES_TODO_EN.md` (English planned-features list).
  - Added `FEATURES_COMPLETED_EN.md` (English completed-features list).
  - Added `CHANGELOG_EN.md` (this file) as the English changelog mirroring the Chinese version.

> Note: Core features (basic CRUD, local storage, initial HUD, and the base Permission Center) were already present in the initial 1.0.0 release. This entry records the recent incremental changes. Future versions will append new sections to this file.
