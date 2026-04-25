# Features (v1.3.0)

TodoList is a Minecraft todo mod for single-player, LAN, and multiplayer collaboration. This document describes what is available in v1.3.0 from a user perspective.

## Task Management

- Create, edit, delete, and complete/uncomplete tasks in an in-game GUI
- Task fields: title, description, priority (Low/Medium/High), and tags (comma-separated)
- Search & filters: real-time filtering by status, priority, text (title/description), and tags
- Unfinished tasks can be reordered directly with drag-and-drop
- Scrollable task list: mouse wheel scrolling + draggable scrollbar; selection highlight and hover feedback
- Completed tasks now have a clearer split from open tasks, paired with a more focused detail-drawer editing flow
- Task deletion now uses an explicit confirmation step to reduce accidental removals

## Projects & Views

- Supports both personal projects and team projects
- Sidebar project list: search and switch projects; create/edit/delete projects
- Project search now supports prefixes and dropdown hints for faster navigation in larger workspaces
- Team views (multiplayer): Unassigned, All Assigned, Assigned to Me
- Team projects support membership management and join requests
- Starred projects are sorted first and can be reused as HUD sources
- Team projects can optionally enable an all-player task mode for broader shared workflows

## Multiplayer Collaboration & Permissions

- In multiplayer, personal tasks are persisted per player; team tasks are stored on the server and synchronized to players
- Team-view edits must be submitted via Save; Cancel or closing with Esc discards local unsaved edits and refreshes from the server to reduce conflicts
- A unified server-side Permission Center validates all team operations based on role, view scope, completion state, and assignment relationship
- The “Assign Others” dialog now consistently uses project members, and offline members remain assignable
- Role differences:
  - Admins can fully manage team tasks (add/edit/delete/assign/complete)
  - Regular players can claim/abandon tasks and complete tasks assigned to them; “Assign Others” is visible to admins only

## Assignment Workflow

- Supports claim, abandon, and assign; the “Assigned to Me” view helps focus on what you need to do next
- Assignee name labels can fall back for offline players, keeping GUI/HUD tags readable
- Feedback around claim / abandon / assign / complete interactions has been strengthened to reduce “action succeeded but UI looks stale” moments

## HUD Display & Config

- Top-right HUD shows tasks for the current view, supports expand/collapse (H key) and count display
- HUD header reflects the current view (Personal / Team-Unassigned / Team-All Assigned / Team-Assigned to Me)
- In true single-player worlds, the HUD default view is locked to Personal and team views are hidden
- Built-in HUD config screen: width, max height, todo/done item limits (0–30), default expanded state, show-when-empty, default view, and draggable preview positioning; supports Save & Apply
- If Mod Menu is installed, the HUD config screen can also be opened from the mod entry
- HUD semantics are now aligned with the GUI for priority color blocks, hidden-count summaries, and current-view filtering

## Persistence & Localization

- Safer file-based persistence is used for tasks, projects, player project state, and config data
- When a primary data file is corrupted, the mod can recover automatically from the latest backup while preserving the corrupt copy
- Local singleplayer personal tasks now keep local-file and per-player-file copies in sync to reduce accidental rollback-like restores
- Bilingual UI via language packs (English and Chinese), following the game language

## Audit Logs

- The server logs all effective team operations in a unified `[TEAM_OP]` format for auditing and troubleshooting

