# AirFloat Android Release Handoff

This repository contains the Android source needed to build AirFloat.

## Verified Local Builds

Verified on 2026-05-13:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:assembleRelease`
- `./gradlew :app:bundleRelease`

The Play Store artifact is:

- `app/build/outputs/bundle/release/app-release.aab`

## Requirements

- Android Studio
- JDK 17
- Android SDK / compile SDK 34
- Google Play Console access for the publishing account

## Build

```bash
./gradlew :app:bundleRelease
```

## Publishing Notes

The repository intentionally does not include release signing keys.

The publisher must provide:

- Google Play Console app access
- upload key / app signing setup
- final app listing metadata
- final screenshots and privacy declarations

Current package metadata:

- `applicationId`: `com.airfloat.app`
- `versionName`: `1.0`
- `versionCode`: `1`
- `minSdk`: `26`
- `targetSdk`: `34`

If the app is published under a different company/account, the publisher may choose to change `applicationId` before the first Play Store release. After the first release, changing it creates a different Android app.

## Known Handoff Boundaries

- Debug and release builds compile.
- The generated release AAB is buildable locally.
- Play Store publication still requires the publisher's signing and store account.
- Local files such as `local.properties`, keystores, APK/AAB outputs, and research datasets are intentionally excluded from Git.

