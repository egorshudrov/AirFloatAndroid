# AirFloat Android

AirFloat is an Android fitness app for real-time exercise tracking. It uses the phone camera, on-device pose estimation, and deterministic movement logic to count reps, show form feedback, and turn completed workouts into progress insights.

## Highlights

- Real-time camera pipeline with MediaPipe Tasks Vision
- Live skeleton overlay and rep counter
- Exercise support for Barbell Press, Dumbbell Press, Squat, Push-up, and Sit-up
- Today, Train, Live, and Progress surfaces
- Offline-first local session history
- Attempt-level workout analytics
- Release build and Android App Bundle support

## Tech Stack

- Kotlin
- Android SDK 34, min SDK 26
- CameraX
- MediaPipe Tasks Vision
- Material Components
- Custom Android Views for charts and training surfaces
- JUnit 4

## Project Structure

```text
app/src/main/java/com/airfloat/app/
  MainActivity.kt       App shell, camera flow, workout orchestration
  pose/                 Camera frame conversion, pose detection, overlay utilities
  squat/                Squat counter and classifier logic
  pushup/               Push-up counter and classifier logic
  situp/                Sit-up counter and classifier logic
  stats/                Local session storage, analytics, progress state
  ui/                   Today, Train, Progress, charts, and custom views
  record/               Optional workout screen recording service
```

Model and runtime assets live in:

```text
app/src/main/assets/
```

## Build

Requirements:

- Android Studio
- JDK 17
- Android SDK 34

Debug build:

```bash
./gradlew :app:assembleDebug
```

Release APK:

```bash
./gradlew :app:assembleRelease
```

Android App Bundle:

```bash
./gradlew :app:bundleRelease
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Release Handoff

Publishing notes are documented in [RELEASE_HANDOFF.md](RELEASE_HANDOFF.md).

The repository does not include Play Console credentials, upload keys, local SDK paths, or generated build outputs. Those belong to the publisher's environment.

## Status

Current verified commands:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:assembleRelease`
- `./gradlew :app:bundleRelease`

