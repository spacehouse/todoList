# Planned Features (Todo List)

This document tracks planned and partially implemented features for the Todo List mod.

## 🚀 Phase 2: Enhanced Features (Planned)

### Command System
- ⏳ In-game commands
  - `/todo add <title> [description] [tags]` - quickly add a task
  - `/todo list` - list all tasks
  - `/todo complete <ID>` - complete a task
  - `/todo delete <ID>` - delete a task
  - `/todo clear` - clear all tasks

### GUI Enhancements
- ⏳ Tag system improvements
  - Filter by tag
  - Custom tag colors

- ⏳ Task sorting
  - Sort by creation time
  - Sort by completion status

- ⏳ Subtasks
  - Add subtasks to a task
  - Show subtask progress
  - Parent task completion rules

- ⏳ Due dates
  - Set task due dates
  - Reminders for upcoming deadlines
  - Highlight overdue tasks

### Configuration System
- ⏳ Extended mod config GUI
  - HUD toggle
  - Auto-save interval
  - Default priority
  - Dark theme toggle

---

## 🌐 Phase 3: Multiplayer Server Support (Partially Implemented)

### Network Sync
- ✅ Client-server task sync (basic functionality implemented in v1.0.0)
  - Sync personal and team tasks when players join
  - Submit team changes via a **Save** button; the server validates permissions and broadcasts the latest result
  - In team views, after clicking **Save**, **Cancel**, or closing with **Esc**, the client re-syncs team tasks from the server to ensure consistency

### Server-Side Storage
- ✅ Per-player data (implemented)
  - Each player has an independent personal task list
  - Server-side file storage
  - Use UUID as player identifier

- ✅ Shared team tasks (basic implementation)
  - Single global team task list stored on the server
  - Team collaboration views (Unassigned / All Assigned / Assigned to Me)
  - Centralized permission management and operation logging

### Network Optimization
- ⏳ Packet optimizations
  - Incremental updates
  - Batch sync
  - Compressed payloads

---

## 🎮 Phase 4: Gamification (Planned)

### Task Book Item
- ⏳ Item-based task list
  - A book-like item representing the task list
  - Right-click to open the task GUI
  - Can be given to other players

### Task Rewards
- ⏳ Reward system on completion
  - XP rewards
  - Item rewards
  - Customizable reward configuration

- ⏳ Achievement system
  - Achievements for completing a certain number of tasks
  - Streak achievements (consecutive days with completed tasks)
  - Achievements for specific task types

### Integrations
- ⏳ Signboard integration
  - Right-click signs to show tasks
  - Public task board
  - Server announcements

- ⏳ Chat integration
  - Task completion notifications
  - Due date reminders
  - Task assignment messages

### Automated Tasks
- ⏳ Game-event triggered tasks
  - Kill specific mobs
  - Collect specific items
  - Explore biomes

- ⏳ Progress tracking
  - Automatically detect completion conditions
  - Real-time progress display
  - Percentage progress indicators

### Advanced Features
- ⏳ Task templates
  - Predefined common task templates
  - Save custom templates
  - Quick create from templates

- ⏳ Task statistics
  - Number of tasks completed
  - Average completion time
  - Distribution by task type

- ⏳ Import / Export
  - Backup task lists
  - Cross-world data migration
  - JSON export format

---

## 🔧 Technical Improvements (Planned)

### Performance Optimization
- ⏳ Large task list optimization
  - Virtual scrolling
  - Lazy loading
  - Caching mechanisms

### Code Refactoring
- ⏳ Platform abstraction layer
  - Separate core logic from platform-specific code
  - Prepare for Forge / NeoForge ports

### Version Compatibility
- ⏳ Multi-version support
  - Latest 1.20.x
  - 1.21.x adaptation

- ⏳ NeoForge port
  - 1.20.1+ NeoForge version
  - 1.21+ NeoForge version

---

## 📝 Documentation (Planned)

### User Documentation
- ⏳ Usage guide
- ⏳ Command reference
- ⏳ Configuration guide
- ⏳ FAQ

### Developer Documentation
- ⏳ API reference
- ⏳ Contribution guide
- ⏳ Architecture overview

---

## 🐛 Known Issues (Resolved)

See `Bugs.md` for a list of fixed issues.

---

*Last Updated: 2026-02-18*  
*Version: 1.0.0*

