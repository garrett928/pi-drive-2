---
name: update-plan
description: Update the Pi Drive implementation plan when requirements, design, or implementation details change. Surgically edits only affected files to minimize token usage. Works for both the Android app (pi-drive-android/) and the Flask server (pi-drive-server/).
---

# Update Plan

Synchronize the implementation plan, progress tracker, and project docs when something changes.

## Route by project

First, determine which project the change applies to:

| Signals in the request | Project | Plan root |
|---|---|---|
| Android, Kotlin, Compose, OBD, Bluetooth, Activity, on-device logging, phase-0x to phase-13 | **Android** | `pi-drive-android/implementation/` |
| Flask, Python, server, Postgres, Timescale, Docker, K8s, Alloy, phase-0x to phase-08 (server) | **Server** | `pi-drive-server/implementation/` |
| Grafana Loki / log shipping — applies to **both**; the app pushes Loki-format logs (Android Phase 13), the server logs JSON for Alloy (Server Phase 8). Update both and keep the shared label/metadata schema in sync. |  |
| Ambiguous | Ask the user which one, or check `PROGRESS.md` in each to see which is active |

---

## Android plan: reference files

| File | Purpose |
|---|---|
| `pi-drive-android/REQUIREMENTS.md` | Product spec |
| `pi-drive-android/README.md` | OBD/BT protocol reference |
| `pi-drive-android/TESTING.md` | Testing approach |
| `pi-drive-android/implementation/IMPLEMENTATION.md` | Master plan overview |
| `pi-drive-android/implementation/PROGRESS.md` | Step tracker |
| `pi-drive-android/implementation/phase-NN-*.md` | Phase detail files |

## Server plan: reference files

| File | Purpose |
|---|---|
| `pi-drive-server/REQUIREMENTS.md` | Product spec |
| `pi-drive-server/SERVER_DEVELOPER_DOCS.md` | Java-server lessons (history) |
| `pi-drive-server/implementation/IMPLEMENTATION.md` | Master plan overview |
| `pi-drive-server/implementation/PROGRESS.md` | Step tracker |
| `pi-drive-server/implementation/phase-NN-*.md` | Phase detail files |

---

## Procedure (same for both projects)

### 1. Identify the change scope

Read ONLY the changed file or section — not all docs.

**Change type → Files to check:**

| What changed | Read | Potentially update |
|---|---|---|
| Requirement | The changed section only | Phase file(s) that reference that feature |
| Protocol/API detail | The changed section only | Phase file(s) that reference that API |
| Testing approach | The changed section only | Phase file(s) with affected verify steps |
| Implementation detail (wrong approach) | The specific phase file | That phase file only |
| Step completed | Nothing extra | `PROGRESS.md` only |
| New feature added | Requirements section | `IMPLEMENTATION.md` (add step), new or existing phase file |
| Feature removed | Requirements section | Phase file (remove step), `IMPLEMENTATION.md`, `PROGRESS.md` |

### 2. Trace the impact

Search phase files for keywords from the changed area:

```bash
# Android
grep -l "keyword" pi-drive-android/implementation/phase-*.md

# Server
grep -l "keyword" pi-drive-server/implementation/phase-*.md
```

Read only the affected sections.

### 3. Make surgical edits

Edit only the lines that need to change. Do NOT rewrite entire files. Use the Edit tool with precise `old_string` → `new_string` replacements.

**Rules:**
- Keep phase file structure intact (step numbers, headers, test criteria format).
- If scope changes significantly, update the step's estimated size.
- If a new dependency is introduced, update the "Depends on" line and the dependency graph in `IMPLEMENTATION.md`.
- If a step is added or removed, update the step count in `IMPLEMENTATION.md` and the step table in `PROGRESS.md`.

### 4. Update PROGRESS.md (if needed)

If step status changed:
- Update the status column in the step table.
- Update "Active step" and "Current State" if the active step changed.
- Add notes at the bottom if there's context future agents need.

### 5. Cross-reference check

After editing, verify consistency:
- Step numbers in phase file match step numbers in `PROGRESS.md`.
- Dependencies in phase files are satisfiable (no circular, no missing prereqs).
- Total step count in `IMPLEMENTATION.md` matches actual steps across phase files.

### 6. Report

Tell the user:
- Which project was updated (Android / Server).
- What changed and why.
- Which files were updated.
- Any downstream impacts.

---

## Examples

**Server: wire contract path changed**
1. Read `pi-drive-server/REQUIREMENTS.md` §5 for the change.
2. `grep -l "/telemetry" pi-drive-server/implementation/phase-*.md` → finds `phase-02-ingest.md`.
3. Edit only the affected lines in `phase-02-ingest.md`.
4. Done — no need to touch `IMPLEMENTATION.md` or `PROGRESS.md`.

**Android: new feature "DTC reading screen"**
1. Read `pi-drive-android/REQUIREMENTS.md` for the new feature.
2. Decide which phase it fits (Phase 8 settings or a new step).
3. Add the step to the phase file with full details.
4. Update `IMPLEMENTATION.md` step count.
5. Add a row to `PROGRESS.md` step table.

**Step completed (either project)**
1. No reads needed.
2. Update `PROGRESS.md`: status → DONE, PR/Commit, advance Active step, add to Completed summary.
