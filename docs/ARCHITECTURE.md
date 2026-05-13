# AirFloat Android Architecture

AirFloat is built around a local, camera-first training loop:

```text
CameraX frame
  -> MediaPipe pose landmarks
  -> exercise-specific counter
  -> live UI feedback
  -> local session record
  -> progress analytics
```

## Runtime Flow

1. `MainActivity` owns the main workout runtime and coordinates camera, pose, selected exercise, and screen transitions.
2. `pose/PoseDetector.kt` wraps the MediaPipe Tasks Vision integration.
3. Exercise counters convert pose landmarks into explicit movement states and rep decisions.
4. Session data is persisted locally through the stats layer.
5. Progress views transform saved sessions into charts, calendars, and latest-session summaries.

## Exercise Logic

Exercise-specific movement code is intentionally separated:

- `squat/`
- `pushup/`
- `situp/`

This keeps pose acquisition separate from movement interpretation and makes it easier to tune each exercise independently.

## Data Model

The app stores workout sessions and attempt-level details locally. A session captures the workout summary; attempts capture clean reps, misses, timing, and feedback reasons.

The Progress surface is built from persisted local sessions, so the core loop works offline and does not require a backend for the MVP.

## Publishing Boundary

The repository contains source code and reproducible build configuration. Store credentials, signing keys, generated APK/AAB files, and publisher account setup are intentionally outside version control.

