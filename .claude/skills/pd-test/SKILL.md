---
description: Run Pi Drive's unit and instrumented test suite. Use after implementing or modifying any feature, when asked to "run tests", "check tests", "verify tests pass", or before marking a feature complete. Report pass/fail counts and full failure details.
---

Run Pi Drive's test suite and report results clearly.

Arguments (optional): a module name to narrow the run — `shared`, `mobile`, or `all`. Defaults to `all`.

Module: $ARGUMENTS

Do the following steps in order.

## Step 1 — Run unit tests (no device required)

From the `pi-drive-android/` directory:

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
```

If module is `shared`:
```bash
./gradlew :shared:test
```

If module is `mobile`:
```bash
./gradlew :mobile:test
```

Otherwise (all, or no argument):
```bash
./gradlew :shared:test :mobile:test
```

## Step 2 — Parse results

After the run, read the JUnit XML test result files to get exact failure details:

- `shared/build/test-results/testDebugUnitTest/*.xml`
- `mobile/build/test-results/testDebugUnitTest/*.xml`

For each XML file, find any `<testcase>` elements that contain a `<failure>` or `<error>` child.

## Step 3 — Report

Print a summary in this format:

```
Unit tests: X passed, Y failed, Z skipped

FAILURES:
  ClassName > test name
    Expected: ...
    Actual:   ...
    at FileName.kt:line
```

If all tests pass, say so clearly and give the total count.

## Step 4 — Run instrumented tests (optional, device required)

Only do this step if $ARGUMENTS contains the word `instrumented` or `connected`.

Check for a running device:
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

If a device is available:
```bash
./gradlew :mobile:connectedDebugAndroidTest
```

Report results from `mobile/build/outputs/androidTest-results/connected/`.

If no device is available and instrumented tests were requested, say so and suggest running `/pd-run` first.
