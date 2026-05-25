---
description: Capture a screenshot of Pi Drive and visually verify it against the design spec. Use after implementing any UI feature, after navigating to a new screen, when asked to "check the UI", "take a screenshot", "verify the layout", or "compare to the design". Screenshots are required proof — take one after every feature implementation and after every significant navigation step.
---

Capture a screenshot of Pi Drive on the connected device, pull it to disk, and visually verify it against the design spec.

Arguments (optional): the screen name to verify against, e.g. `home`, `trips`, `connect`, `settings`, `aa-dials`, `aa-graphs`, `aa-split`. If omitted, just captures and describes what's visible without comparing to a spec.

Screen: $ARGUMENTS

Do the following steps in order.

## Step 1 — Check for a connected device

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

If no device shows `device` status, stop and suggest running `/pd-run` first.

## Step 2 — Capture screenshot

```bash
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/pidrive_screen.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/pidrive_screen.png /tmp/pidrive_screen.png
```

## Step 3 — Read and describe the screenshot

Use the Read tool to open `/tmp/pidrive_screen.png`. Describe what is visible: which screen is showing, what data values appear, layout structure, colors, and any obvious visual problems (blank sections, overlapping elements, wrong colors, missing text).

## Step 4 — Compare against design spec (if a screen name was given)

Look up the design spec for the named screen:

| Screen arg | Design file | What to look for |
|---|---|---|
| `home` | `ui-handoff/pi-drive/project/pd-screens-phone.jsx` → `ScreenHome` | Featured metric (large mono), MPG row (3 cols), 2-col tile grid, connection banner |
| `trips` | `pd-screens-phone.jsx` → `ScreenTrips` | Weekly summary card, day-grouped list, sync pills, warning badge |
| `connect` | `pd-screens-phone.jsx` → `ScreenConnect` | Scan/pair/done step state, device list with RSSI bars |
| `settings` | `pd-screens-settings.jsx` → `ScreenSettings` | Vehicle card, 4 sections, row layout |
| `server` | `pd-screens-settings.jsx` → `ScreenServer` | Endpoint fields, health card, streaming toggles, sample rate slider |
| `home-layout` | `pd-screens-settings.jsx` → `ScreenHomeLayout` | Featured tile picker, tile grid with drag handles |
| `aa-layout` | `pd-screens-settings.jsx` → `ScreenAALayout` | 3 tabs (Dials/Graphs/Split), preview canvas, widget panel |
| `thresholds` | `pd-screens-settings.jsx` → `ScreenThresholds` | Strategy cards with glow border, slider rows, shared params |
| `aa-dials` | `pd-screens-aa.jsx` → `AAScreenDials` | 3 arc dials (speed/RPM/coolant), bottom stat strip |
| `aa-graphs` | `pd-screens-aa.jsx` → `AAScreenGraphs` | 2 stacked graphs left, MPG+manual trip boxes right |
| `aa-split` | `ui-handoff/pi-drive/project/pd-aa-split.jsx` | ⅓ panel with header, page 1 (hero+pills+graph), page 2 (2×3 grid) |

Read the relevant section of the design file using the Read tool. Then compare the screenshot against it and list:

**Matches:** things that look correct
**Discrepancies:** differences in layout, typography, colors, or content

Use the design token values from `ui-handoff/pi-drive/project/pd-tokens.jsx` when assessing colors (dark bg is approximately `oklch(0.155 0.005 60)`, accent orange is `oklch(0.72 0.17 55)`).

## Step 5 — Report

Summarize:
1. What screen is showing
2. Whether it matches the design (or what's different)
3. Any specific elements that need fixing

If discrepancies are found, suggest the most likely cause (wrong color token, layout parameter, missing component, wrong font) based on the design file.
