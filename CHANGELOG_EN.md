# Changelog (English)

All notable changes to this project will be documented in this file.

Date format: `YYYY-MM-DD`

## [1.0.0] - 2026-02-21

### Capability Overview
- **Task Management**: In-game GUI to create/edit/delete/complete tasks; priorities (Low/Medium/High), tags, and search & filters (status/priority/text/tags).
- **Projects**: Personal and team projects; sidebar project list with search and switching; create/edit/delete projects, with team membership management and join requests.
- **HUD Display & Config**: Top-right HUD list for the current view with expand/collapse and count display; in true single-player, HUD is locked to Personal and hides team views; HUD config screen covers size, item limits, default view, show-when-empty, and draggable preview positioning.
- **Multiplayer Collaboration**: Per-player personal tasks persisted by player UUID; server-shared team tasks synchronized to players; team edits are submitted via Save, while Cancel/Esc discards local changes and refreshes from the server.
- **Assignment Workflow**: Team tasks support claim/abandon/assign and an “Assigned to Me” view; assignee names can fall back for offline players to keep labels readable.
- **Permissions & Audit**: A unified server-side Permission Center checks team operations based on role, view scope, completion state, and assignment; all effective team operations are logged in a unified `[TEAM_OP]` format.
- **Persistence & Localization**: File-based persistence for tasks and projects (single-player and multiplayer); bilingual UI/docs (ZH/EN).
