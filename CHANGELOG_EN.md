# Changelog (English)

All notable changes to this project will be documented in this file.

Date format: `YYYY-MM-DD`

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
