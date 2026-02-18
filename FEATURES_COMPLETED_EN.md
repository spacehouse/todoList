# Completed Features

## ✅ Phase 1: MVP (Minimum Viable Product)

### Core Features
- ✅ **Task Management**
  - Add tasks (title + description)
  - Edit tasks
  - Delete tasks
  - Mark tasks as completed/uncompleted
  - Select and highlight tasks

- ✅ **Task Properties**
  - Task title (up to 100 characters)
  - Task description (up to 255 characters)
  - Completion state (completed/uncompleted)
  - Priority (LOW / MEDIUM / HIGH)
  - Unique ID (UUID)

- ✅ **User Interface**
  - Main GUI screen (opened with `K`)
  - Task list display
  - Input fields (title, description, tags)
  - Actions: add task, delete task, save list
  - Toggle completion via checkbox
  - Filter buttons (Active, Completed, High/Medium/Low priority)
  - Top search box to filter tasks by title/description/tags in real time

### Data Persistence
- ✅ **Local Storage**
  - Save tasks in NBT format
  - Auto-save when closing the GUI
  - Manual save button
  - Load previously saved tasks

### User Experience
- ✅ **Keyboard Shortcuts**
  - `K` to open the todo list
  - `Esc` to close the GUI

- ✅ **Mouse Interactions**
  - Scroll the task list with the mouse wheel
  - Drag the scroll bar
  - Click to select tasks

- ✅ **Visual Feedback**
  - Completion state checkbox
  - Priority color coding
  - Hover highlight effects
  - Visible scroll bar

### Localization
- ✅ Chinese UI
- ✅ English UI
- ✅ All GUI, HUD and config texts are driven by language keys and follow the game language

---

## ✅ Phase 2: Enhanced Features (Completed Parts)

### HUD Display
- ✅ **HUD Task List**
  - Show the todo list in the top-right corner of the screen
  - Display counts for active and completed tasks
  - Show tasks sorted by priority
  - Support expand/collapse (toggled with `H`)
  - Refresh HUD content immediately after saving in the GUI
  - HUD header shows the current view (Personal / Team-Unassigned / Team-All Assigned / Team-Assigned to Me)
  - In true single-player worlds, the HUD default list view is fixed to Personal and team views are hidden from HUD

### GUI Enhancements
- ✅ **Priority Selector**
  - Three priority buttons (High/Medium/Low) in the GUI
  - Choose priority when creating a task
  - Change priority when editing a task
  - Priority buttons and list markers use consistent colors

- ✅ **Basic Tag Support**
  - Tasks support adding/editing comma-separated tags
  - First tag is displayed in the GUI list
  - HUD shows task tags
  - Tags are color-marked in both GUI and HUD to improve readability

- ✅ **Completion Rules**
  - Completed tasks are read-only (title, description, tags and priority cannot be edited)
  - Only completed tasks can be deleted

### Configuration System
- ✅ **HUD Config Screen**
  - Configure HUD width and maximum height
  - Use sliders to configure how many active/completed tasks to show (0–30)
  - Configure default expanded/collapsed state
  - Use toggle buttons for “Default expanded” and “Show HUD when empty”
  - Preview and drag HUD position in the config screen
  - Support custom HUD coordinates (top-right as default anchor)
  - Switch HUD default list view (Personal / Team-Unassigned / Team-All Assigned / Team-Assigned to Me); in true single-player worlds this option is fixed to Personal and the switch button is disabled

### Multiplayer & Team Tasks
- ✅ **Team Task List & Permission Control**
  - Support personal view and team views (Unassigned / All Assigned / Assigned to Me)
  - Team tasks are stored on the server and synchronized to all players
  - Admins can fully manage team tasks (add, edit, delete, assign, complete)
  - Regular members can only perform “claim, abandon, toggle complete” on team tasks and must click Save to submit changes in batch
  - When saving, the server performs permission and conflict checks. If tasks were modified by others, the client list is refreshed and a message is shown
  - In team views, after clicking Save, Cancel or closing with Esc, the client requests the latest team task list from the server, discarding unsaved local changes and keeping the list consistent with server state
  - HUD team views match GUI semantics exactly: Unassigned shows only unassigned tasks, All Assigned shows only tasks with an assignee, and Assigned to Me shows only tasks assigned to the current player
  - Team task operations (toggle complete, claim, abandon, assign others) are validated by a unified server-side Permission Center
  - All validated and effective team task operations are recorded in the server operation log in a unified format, including player name, operation type, task ID and change details
  - In team views, regular players only see “Claim Task” and “Abandon” buttons; the “Assign Others” button is visible only to admins

### List & Sorting Behaviour
- ✅ When filtering by priority, uncompleted tasks are always shown before completed tasks

---

## ✅ Bug Fixes

### UI Layout Issues
- ✅ Fixed title overlapping with filter buttons
- ✅ Fixed complete button overlapping with the task list
- ✅ Optimized display area for task tags and player names in both GUI and HUD (taking scroll bar width into account, adding ellipsis, preventing text overflow)

### Functional Issues
- ✅ Implemented mouse wheel scrolling
- ✅ Implemented scroll bar dragging
- ✅ Implemented save list feature (auto-save + manual save button)
- ✅ Fixed an issue where, after switching views and switching back, the previously selected task’s title could become empty due to stale selection
- ✅ Fixed an issue where HUD could still show team views after switching from a server world back to single-player
- ✅ Fixed an issue where cancelling or closing with Esc in team views did not properly discard unsaved changes and re-sync from the server

---

## 📊 Current Status

**Version**: 1.0.0  
**Minecraft Version**: 1.20.1  
**Mod Loader**: Fabric 0.14.21+  
**Build Status**: ✅ Successful  

---

*Last Updated: 2026-02-18*

