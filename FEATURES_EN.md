# Features (v1.0.0)

TodoList is a Minecraft (Fabric) todo mod that works in both single-player and multiplayer servers. This document describes what is already available in v1.0.0 from a user perspective.

## Task Management

- Create, edit, delete, and complete/uncomplete tasks in an in-game GUI
- Task fields: title, description, priority (Low/Medium/High), and tags (comma-separated)
- Search & filters: real-time filtering by status, priority, text (title/description), and tags
- Scrollable task list: mouse wheel scrolling + draggable scrollbar; selection highlight and hover feedback
- Completed tasks become read-only (cannot edit content/priority), but can still be deleted

## Projects & Views

- Supports both personal projects and team projects
- Sidebar project list: search and switch projects; create/edit/delete projects
- Team views (multiplayer): Unassigned, All Assigned, Assigned to Me
- Team projects support membership management and join requests

## Multiplayer Collaboration & Permissions

- In multiplayer, personal tasks are persisted per player; team tasks are stored on the server and synchronized to players
- Team-view edits must be submitted via Save; Cancel or closing with Esc discards local unsaved edits and refreshes from the server to reduce conflicts
- A unified server-side Permission Center validates all team operations based on role, view scope, completion state, and assignment relationship
- Role differences:
  - Admins can fully manage team tasks (add/edit/delete/assign/complete)
  - Regular players can claim/abandon tasks and complete tasks assigned to them; “Assign Others” is visible to admins only

## Assignment Workflow

- Supports claim, abandon, and assign; the “Assigned to Me” view helps focus on what you need to do next
- Assignee name labels can fall back for offline players, keeping GUI/HUD tags readable

## HUD Display & Config

- Top-right HUD shows tasks for the current view, supports expand/collapse (H key) and count display
- HUD header reflects the current view (Personal / Team-Unassigned / Team-All Assigned / Team-Assigned to Me)
- In true single-player worlds, the HUD default view is locked to Personal and team views are hidden
- Built-in HUD config screen: width, max height, todo/done item limits (0–30), default expanded state, show-when-empty, default view, and draggable preview positioning; supports Save & Apply
- If Mod Menu is installed, the HUD config screen can also be opened from the mod entry

## Persistence & Localization

- File-based persistence for tasks and projects (both single-player and multiplayer)
- Bilingual UI via language packs (English and Chinese), following the game language

## Audit Logs

- The server logs all effective team operations in a unified `[TEAM_OP]` format for auditing and troubleshooting

