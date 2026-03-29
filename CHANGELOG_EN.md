# Changelog (English)

All notable changes to this project will be documented in this file.

Date format: `YYYY-MM-DD`

## [1.2.2] - 2026-03-29

### Improvements & Fixes
- **HUD State Sync Improvements**: Strengthened HUD visibility synchronization to keep client and server display state more consistent.
- **LAN Team Sync Improvements**: Optimized team project and team task synchronization in published LAN worlds for more stable multiplayer collaboration.
- **Local Personal Task Isolation**: Refactored personal task storage to improve data isolation in local single-player environments and reduce cross-environment interference.
- **Cross-Project Save Fix**: Fixed save issues when switching across projects to avoid incorrect overwrites or missed changes.
- **LAN Personal Task Restore Fix**: Fixed incorrect personal task restoration in LAN scenarios, improving data stability when switching between local and networked sessions.
- **NeoForge Sync Chain Fix**: Completed HUD sync and LAN state restore handling on NeoForge, improving cross-loader behavior consistency.
- **Assign Dialog Member Source Fix**: Fixed the “Assign Others” dialog showing online players instead of the current team project members; offline members can now still be displayed and assigned correctly.
- **Assign Dialog Layout Fix**: Improved adaptive layout for the “Assign Others” dialog so the member list shrinks with the screen size and the Cancel button stays visible.

### Testing & Stability
- **Offline Self-Test Coverage**: Expanded offline self-test support for GUI components, command flows, and submission workflows to improve regression verification.
- **Integration Test Expansion**: Added and split command-system integration test entry points, with finer coverage for projects, tasks, and join approval flows.
- **1.21.1 Test Adaptation**: Adapted offline test stubs and GUI submission flow for the `1.21.1` branch to make future maintenance more stable.

## [1.2.1] - 2026-03-21

### Improvements & Fixes
- **Team Project Availability Fix**: Corrected the team project availability check logic on local single-player servers to ensure project states and permissions are displayed and handled correctly.

## [1.2.0] - 2026-03-19

### New Features
- **Platform Support Upgrade**: Officially supports Minecraft `1.21.1` client/server release and runtime on Fabric, Forge, and NeoForge.

### Improvements & Fixes
- **Forge Network Load Fix**: Fixed the `Failed to create Forge channel` startup failure by improving channel creation compatibility and diagnostic logging.
- **Fabric GUI Readability Fix**: Unified task/project related screens to use clear solid backgrounds, removing blurred overlays that reduced text readability.
- **NeoForge Key Binding Fix**: Restored `H/J/K` registration and trigger flow on NeoForge so bindings are visible in Controls and actions work in-game.
- **Release Pipeline Upgrade**: Added NeoForge publishing, updated Fabric/Forge game versions to `1.21.1`, unified GitHub release title to `TodoList-vX.Y.Z-release`, and fixed tag-based release note extraction from `CHANGELOG.md`.

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
