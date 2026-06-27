# Roadmap

This roadmap now focuses only on work that is still planned after the `1.4.0` release.

The following items are already delivered in the `1.4.0` baseline and are no longer tracked here as future roadmap items:

- Core command-system coverage
- H2 storage, TCP external access, and H2 maintenance commands
- One-click cleanup for completed tasks in the GUI
- GUI editing auto-save toggle

## 1.4.0 Delivered Baseline

- `/todo` now covers task, project, HUD, join-approval, admin, and H2 maintenance flows
- H2 now includes `NBT -> H2` migration, TCP external access, online backup, health checks, database reload, and password reset
- H2 mode now includes query optimization for GUI/HUD paths and key lifecycle fixes
- The GUI now includes one-click cleanup for completed tasks and an auto-save toggle for detail editing

## Phase 4: Gamification

- Task book item: represent the todo list as an in-game item; hold/right-click to open GUI; enable sharing and delivery flows
- Task rewards: grant XP, items, or scoreboard points on completion; support configurable reward rules
- Achievements & milestones: unlock badges/titles by completion count, streak days, or themed goals
- Public task board: integrate with signs/specific blocks to display team tasks, announcements, and progress
- Notifications & feedback: show key events (claim/assign/complete) via chat or on-screen prompts
- Auto tasks & progress tracking: generate tasks from in-game events, auto-evaluate completion, and show real-time progress
- Stats & insights: provide completion trends and personal/team contribution overviews

## 1.4.x Follow-up Work

- H2 operations UX: improve status visibility, connection examples, backup guidance, and troubleshooting docs
- Command usability: add more batch operations, clearer help output, and more consistent permission messaging
- GUI/HUD stability: continue tightening regressions around cross-project switching, multiplayer sync, and high-frequency editing flows

