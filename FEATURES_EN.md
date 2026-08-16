# Features (v1.4.1)

TodoList is a Minecraft todo mod for single-player, LAN, and multiplayer collaboration. This page is written for regular players and focuses on what you can directly use in v1.4.1.

## Task Management

- Create, edit, delete, and complete/uncomplete tasks in an in-game GUI
- Subtasks are supported, so larger goals can be broken down into smaller steps
- Task fields: title, description, priority (Low/Medium/High), and tags (comma-separated)
- Search & filters: real-time filtering by status, priority, text (title/description), and tags
- Unfinished tasks can be reordered directly with drag-and-drop
- Parent tasks can batch-handle their direct subtasks, which reduces repetitive clicking
- Completed tasks now have a clearer split from open tasks, can be folded, and support one-click cleanup within the current project
- Both task deletion and completed-task cleanup now use explicit confirmation steps to reduce accidental operations
- Detail editing now supports an auto-save toggle; when enabled, edits can be saved on blur, task switch, project switch, and screen close

## Projects & Views

- Supports both personal projects and team projects, so it works for solo play as well as group play
- The sidebar lets you search, switch, create, edit, and delete projects
- When you have many projects, search prefixes and dropdown hints help you find the right one faster
- In multiplayer, team tasks can be viewed through Unassigned, All Assigned, and Assigned to Me views
- Team projects support member management, join requests, and team permission settings
- Starred projects are sorted first and can also be used as HUD sources
- Team projects can optionally enable an all-player task mode for more open shared workflows

## Multiplayer Collaboration & Permissions

- In multiplayer, personal tasks are saved per player, while team tasks are stored on the server and synced to members
- Changes in team views need to be confirmed with Save; Cancel or closing with Esc discards local unsaved edits and reloads the latest server state
- The “Assign Others” dialog shows project members directly, and offline members can still be assigned
- Parent tasks can now complete or uncomplete their direct subtasks in one step, and team projects can also assign or claim remaining unassigned direct subtasks in one step
- In the "Assigned to Me" view, parent-task batch complete, uncomplete, and abandon actions only affect direct subtasks claimed by the current player; completed subtasks also stay visible there so they can be rolled back later
- Admins can manage team tasks, members, and critical commands
- Regular players can claim, abandon, and complete tasks assigned to them; “Assign Others” is shown to admins only

## Command System

- Provides a unified `/todo` command entry, while `/todolist` remains available as an alias
- Commands can quickly add, complete, delete, claim, and abandon tasks, and also help with project switching and HUD visibility
- Managers can also use commands for team-member management, H2 status checks, backups, database reloads, and health checks

## HUD Display & Config

- The top-right HUD shows tasks for the current view, supports expand/collapse (H key), and displays count summaries
- The HUD title changes with the current view, so it is easier to tell whether you are looking at personal or team tasks
- In true single-player worlds, the HUD stays on personal tasks and hides team views
- The built-in HUD config screen lets you adjust width, height, item limits, default expanded state, empty-list display, and draggable position, then apply the settings directly
- If Mod Menu is installed, the HUD config screen can also be opened from the mod entry
- HUD colors, counts, and filtering stay consistent with the main GUI, which makes day-to-day use easier to read

## H2 Storage & Maintenance

- The mod now uses `H2` as its unified storage mode; old NBT data is migrated automatically the first time it is needed, so manual steps are usually unnecessary
- Tasks, projects, and project-state data continue to be saved normally
- If you run a server or manage a world, you can use `/todo h2 status` to check the current storage status
- When troubleshooting, `/todo h2` commands can also create backups, reload the database, and run health checks

## Persistence & Localization

- Tasks, projects, player project state, and config data are saved with a safer persistence flow
- If a primary data file is damaged, the mod can try to recover from the latest backup while preserving the damaged copy for troubleshooting
- Local single-player personal tasks keep extra synchronization protection to reduce accidental rollbacks or clears
- The UI includes both English and Chinese language packs and follows the game language

## Audit Logs

- The server logs important team-task actions, which helps server owners troubleshoot issues

