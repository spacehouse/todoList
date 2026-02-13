# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Todo List Mod for Minecraft 1.20.1 (Fabric) - A simple and powerful todo list mod supporting both single-player and multiplayer modes.

**Tech Stack:**
- Minecraft 1.20.1
- Fabric Loader 0.14.21+
- Fabric API 0.87.0+
- Java 17
- Gradle 8.1.1

## Build Commands

```bash
# Build the mod
./gradlew build

# Run client (for testing)
./gradlew runClient

# Run server (for testing)
./gradlew runServer

# Clean build
./gradlew clean

# Generate VS Code/Eclipse/IntelliJ project files
./gradlew eclipse
./gradlew idea
```

## Architecture

### Module Structure
The project is organized with platform-agnostic core code and platform-specific implementations:

```
com.todolist/
├── TodoListMod.java          # Main entry point (common)
├── client/                   # Client-specific code
│   └── TodoClient.java       # Key bindings, HUD, client events
├── config/                   # Configuration management
│   └── ModConfig.java        # JSON config handling
├── gui/                      # GUI components
│   ├── TodoScreen.java       # Main todo list screen
│   └── TaskListWidget.java   # Scrollable task list widget
├── network/                  # Network packets
│   └── TaskPackets.java      # Client-server sync
└── task/                     # Core task logic (platform-agnostic)
    ├── Task.java             # Task entity with NBT serialization
    ├── TaskManager.java      # CRUD operations, filtering
    └── TaskStorage.java      # File I/O, NBT persistence
```

### Data Flow

**Single Player:**
```
TodoScreen (GUI) → TaskManager → TaskStorage → NBT files
                      ↓
                   Local updates
```

**Multiplayer (Phase 3):**
```
Client GUI → Network Packet → Server TaskManager → TaskStorage
                                      ↓
                              Broadcast to all clients
```

### Key Design Decisions

1. **NBT for Storage**: Tasks are stored as NBT files (Minecraft's native format)
   - Single player: `saves/worldname/todo/moddata.dat`
   - Multiplayer: `world/todo/players/{uuid}.dat`

2. **Task ID**: Each task has a unique UUID string identifier for reliable tracking

3. **Platform Abstraction**: Core `task/` package is platform-agnostic to enable future Forge/NeoForge ports

4. **Network Sync**: Client-side changes are optimistically updated, then confirmed by server

## Important File Locations

- **Configuration**: `config/todolist.json` (auto-generated on first run)
- **Data Storage**: `saves/worldname/todo/` or `world/todo/players/`
- **Language Files**: `src/main/resources/assets/todolist/lang/`
- **Mod Metadata**: `src/main/resources/fabric.mod.json`

## Key Features by Phase

### Phase 1 (Current - MVP)
- ✅ Basic task CRUD
- ✅ Simple GUI (K key)
- ✅ Local NBT storage
- ✅ Priority levels (low/medium/high)
- ✅ Task filtering

### Phase 2 (Planned)
- HUD display
- Command system (`/todo`)
- Enhanced GUI features

### Phase 3 (Planned)
- Multiplayer synchronization
- Server-side storage
- Network packet system

### Phase 4 (Planned)
- Task book item
- Task rewards
- Sign board integration

## Common Tasks

### Adding a New Task Property

1. Add field to `Task.java`
2. Update `toNbt()` and `fromNbt()` methods
3. Add getter/setter if needed
4. Update `TaskPackets.writeTask()` and `readTask()` for network sync
5. Add GUI controls in `TodoScreen.java` if user-facing

### Creating GUI Components

- Extend `Screen` for full screens
- Use `DrawContext` for rendering
- Add widgets via `addDrawableChild()`
- Handle input via `keyPressed()`, `mouseClicked()`

### Network Packets (Phase 3)

All packets are defined in `TaskPackets.java`:
- Client → Server: ADD_TASK, UPDATE_TASK, DELETE_TASK, TOGGLE_TASK
- Server → Client: SYNC_TASKS, TASK_CONFIRMED

## Testing

```bash
# Test in single player
./gradlew runClient

# Test multiplayer (requires two terminals)
./gradlew runServer
./gradlew runClient
```

Press **K** key in-game to open the todo list GUI.

## Code Style

- Follow Java naming conventions
- Use NBT for data serialization (not JSON)
- Keep platform-specific code in `client/` or `fabric/` packages
- Log important events with `TodoListMod.LOGGER`
- Chinese translations provided for all user-facing strings

## Known Limitations

1. Task `id` field cannot be final (needed for NBT deserialization)
2. Network sync not yet implemented (Phase 3)
3. HUD not yet implemented (Phase 2)
4. No command support yet (Phase 2)

## Future Porting

The modular structure allows easy porting to:
- NeoForge 1.20.1+ (create `forge/` package)
- Newer Minecraft versions (update dependencies in `gradle.properties`)

Core logic in `task/` package should remain unchanged.
