# Android Store Release Notes

## Current Package Metadata

- Application ID: `com.airfloat.app`
- Version name: `1.0`
- Version code: `1`
- Min SDK: `26`
- Target SDK: `34`

## Build Artifact

Generate the Play Store artifact with:

```bash
./gradlew :app:bundleRelease
```

Output:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Publisher Requirements

The publishing owner must provide:

- Google Play Console access
- app signing / upload key setup
- app listing copy
- screenshots
- privacy policy URL
- data safety form
- production, internal, or closed testing track configuration

## Signing

Release signing material is intentionally not stored in this repository. Configure signing through Android Studio or Gradle using the publisher's own keystore and Play Console setup.

