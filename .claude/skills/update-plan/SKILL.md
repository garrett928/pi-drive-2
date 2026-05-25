---
name: update-plan
description: Update the Pi Drive implementation plan when requirements, design, or implementation details change. Surgically edits only affected files to minimize token usage.
---

# Update Plan

Synchronize the implementation plan, progress tracker, and project docs when something changes.

## When to use

- A requirement in `REQUIREMENTS.md` changed
- A design detail in `README.md` changed
- A testing approach in `TESTING.md` changed
- An implementation step needs revision (wrong approach, scope change, new dependency)
- A step was completed and progress needs updating

## Procedure

### 1. Identify the change scope

Determine what changed and what's affected. Read ONLY the changed file (or the specific section). Do NOT read all project docs preemptively.

**Change type -> Files to check:**

| What changed | Read | Potentially update |
|---|---|---|
| Requirement in `REQUIREMENTS.md` | The changed section only | Phase file(s) that reference that feature |
| OBD/protocol detail in `README.md` | The changed section only | `phase-02-obd-protocol.md` or `phase-04-bluetooth.md` |
| Testing approach in `TESTING.md` | The changed section only | Phase file(s) with affected verify steps |
| Implementation detail (wrong approach) | The specific phase file | That phase file only |
| Step completed | Nothing extra | `PROGRESS.md` only |
| New feature added | `REQUIREMENTS.md` section | `IMPLEMENTATION.md` (add step), new or existing phase file |
| Feature removed | `REQUIREMENTS.md` | Phase file (remove step), `IMPLEMENTATION.md`, `PROGRESS.md` |

### 2. Trace the impact

For requirement changes, identify which implementation steps are affected:
- Search the phase files for keywords related to the changed requirement
- Use `grep -l "keyword" implementation/phase-*.md` to find affected files
- Read only the affected sections of those files

### 3. Make surgical edits

Edit only the lines that need to change. Do NOT rewrite entire files. Use the Edit tool with precise `old_string` -> `new_string` replacements.

**Rules:**
- Keep the phase file structure intact (step numbers, headers, test criteria format)
- If a step's scope changes significantly, update its estimated size
- If a new dependency is introduced, update the "Depends on" line in the phase file AND the dependency graph in `IMPLEMENTATION.md`
- If a step is added or removed, update the step count in `IMPLEMENTATION.md` and the step table in `PROGRESS.md`

### 4. Update PROGRESS.md (if needed)

If step status changed:
- Update the status column in the step table
- Update "Active step" and "Current State" if the active step changed
- Add notes at the bottom if there's context future agents need

### 5. Cross-reference check

After editing, verify consistency:
- Step numbers in phase file match step numbers in `PROGRESS.md`
- Dependencies in phase files are satisfiable (no circular deps, no missing prereqs)
- Total step count in `IMPLEMENTATION.md` matches actual steps across phase files

### 6. Report

Tell the user:
- What changed and why
- Which files were updated
- Any downstream impacts to be aware of

## Examples

**Requirement change: "Hard brake threshold default changed from 6.5 to 7.0 mph/s"**
1. Read `REQUIREMENTS.md` section 5.5.2 (only that section)
2. `grep -l "6.5" implementation/phase-*.md` -> finds `phase-05-detection.md`
3. Edit `phase-05-detection.md`: change "default 6.5" to "default 7.0" in DetectionConfig and test cases
4. Done. No need to touch `IMPLEMENTATION.md`, `PROGRESS.md`, or other phase files.

**New feature: "Add DTC reading screen"**
1. Read `REQUIREMENTS.md` to understand the new feature
2. Decide which phase it fits (Phase 8 settings, or new Phase 8.5)
3. Add step to the phase file with full details
4. Update `IMPLEMENTATION.md` step count
5. Add row to `PROGRESS.md` step table
