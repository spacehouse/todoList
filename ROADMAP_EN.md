# Roadmap

This roadmap intentionally contains only two directions:

- Phase 4: Gamification
- Command system

## Command System

- Provide a `/todo` command entry that covers common operations for both personal and team projects
- Representative capabilities:
  - Quick add: `/todo add <title> [description] [tags]`
  - List/query: `/todo list` (with filter/search parameters)
  - Complete/delete: `/todo complete <ID>`, `/todo delete <ID>`, `/todo clear`
  - Team-related commands are validated server-side (permissions) and follow the existing sync workflow

## Phase 4: Gamification

- Task book item: represent the todo list as an in-game item; hold/right-click to open GUI; enable sharing and delivery flows
- Task rewards: grant XP, items, or scoreboard points on completion; support configurable reward rules
- Achievements & milestones: unlock badges/titles by completion count, streak days, or themed goals
- Public task board: integrate with signs/specific blocks to display team tasks, announcements, and progress
- Notifications & feedback: show key events (claim/assign/complete) via chat or on-screen prompts
- Auto tasks & progress tracking: generate tasks from in-game events, auto-evaluate completion, and show real-time progress
- Stats & insights: provide completion trends and personal/team contribution overviews

