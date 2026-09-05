# Roadmap

This roadmap now focuses only on work that is still planned after the `1.4.0` release.

The following items are already delivered in the `1.4.0` baseline and are no longer tracked here as future roadmap items:

- Core command-system coverage
- H2 storage, TCP external access, and H2 maintenance commands
- One-click cleanup for completed tasks in the GUI
- GUI editing auto-save toggle

## Stable/Progressive/Deferred Support Matrix

| Tier | Version Range | Loaders | Strategy |
|---|---|---|---|
| Stable | `1.20.1` | Fabric + Forge | Current primary delivery baseline; prioritize feature completeness and regression stability |
| Stable | `1.21.1` | Fabric + NeoForge | Next mainline baseline and the starting point for 1.21-series expansion |
| Progressive | `1.20.x` | Fabric + Forge (`1.20.5` Fabric-only) | Onboard sub-versions in batches, with diff assessment and regression per batch |
| Progressive | `1.21.x` | Fabric + NeoForge | Onboard sub-versions in batches; use threshold scoring to decide whether to split clusters |
| Deferred | `1.21.x` | Forge | Deferred for now to avoid duplicated effort alongside the NeoForge track |

## Execution Records (2026-03-03)

- Phase 1 (`1.20.1` / Fabric + Forge): execution boundaries and acceptance criteria aligned; the "minimum viable loop" is now the implementation baseline.
- Phase 2 (`1.20.x` / Fabric + Forge): the "candidate screening, diff pre-scan, minimal adaptation, regression, matrix onboarding, merge & release" flow is fixed.
- Phase 3 (`1.21.1` / Fabric + NeoForge): migration boundaries and exit criteria confirmed; no parallel `1.21.x` Forge track.
- Phase 4 (`1.21.x` / Fabric + NeoForge): threshold triggers and diff-cluster splitting rules landed as the default governance for sub-version expansion.

## Execution Records (2026-08-16)

- The `1.20.x` integration branch finished full builds and runtime test verification for `1.20.1`~`1.20.6`; shared-line maintenance is established.
- Diff convergence: Fabric networking payload APIs, Forge HUD events, GUI initial focus, and test infrastructure all converge into platform compatibility layers; the business protocol has no per-version branching.
- `1.20.5` has no Forge release line (official Forge jumps from 49.x on `1.20.4` straight to 50.x on `1.20.6`); it is Fabric-only, and the build path skips Forge via the matrix.
- The version matrix `gradle/version-matrix.properties` is the long-term single source of truth; the `forge_supported` key controls loader combinations.

## Execution Records (2026-09-06)

- The release workflow `.github/workflows/release.yml` is now matrix-driven: a single `v{mod_version}` tag builds and publishes all `1.20.1`~`1.20.6` artifacts to GitHub Releases, CurseForge, and Modrinth (each "Minecraft version x loader" pair is a separate platform version).
- The release procedure is now three steps: bump `mod_version`, add the matching `CHANGELOG.md` section, and push the `v{mod_version}` tag.

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

