# Pi Drive — Release Signing

This document explains how to create an Android signing keystore and configure
the release build to produce a signed APK for Play Store submission.

## Creating a keystore

```bash
keytool -genkey -v \
  -keystore pi-drive-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias pi-drive
```

Store the keystore file **outside** the repository (never commit it to git).
A reasonable location is `~/.android/keystores/pi-drive-release.jks`.

## Configuring signing in build.gradle.kts

Open `pi-drive-android/mobile/build.gradle.kts` and add a `signingConfigs` block
inside the `android {}` block, **before** `buildTypes`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "~/.android/keystores/pi-drive-release.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        keyAlias = System.getenv("KEY_ALIAS") ?: "pi-drive"
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
    }
}
```

Then reference it in the release build type:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }
}
```

## Local signing

For a local signed release build, set environment variables then run:

```bash
export KEYSTORE_PATH=~/.android/keystores/pi-drive-release.jks
export KEYSTORE_PASSWORD=<your-password>
export KEY_ALIAS=pi-drive
export KEY_PASSWORD=<your-password>

cd pi-drive-android
./gradlew :mobile:assembleRelease
```

The signed APK will be at `mobile/build/outputs/apk/release/mobile-release.apk`.

## CI signing (GitHub Actions)

Store the keystore as a GitHub Actions secret:

1. Base64-encode the keystore:
   ```bash
   base64 -i pi-drive-release.jks | pbcopy
   ```
2. Add these GitHub Secrets to the repository:
   - `KEYSTORE_BASE64` — base64-encoded keystore file
   - `KEYSTORE_PASSWORD` — keystore password
   - `KEY_ALIAS` — key alias (e.g. `pi-drive`)
   - `KEY_PASSWORD` — key password

3. In the CI workflow, decode the keystore before building:
   ```yaml
   - name: Decode keystore
     run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > pi-drive-release.jks
     env:
       KEYSTORE_PATH: ${{ github.workspace }}/pi-drive-release.jks
   ```

## Play Store upload

Once signed, upload `mobile-release.apk` (or the AAB variant) via:
- **Google Play Console** — direct upload via the web UI
- **fastlane supply** — automated upload (see `store/` directory for Fastlane config)

> **Note:** The Play Store requires the APK to be signed with a consistent key
> across all releases. Never rotate the signing key after publishing.
