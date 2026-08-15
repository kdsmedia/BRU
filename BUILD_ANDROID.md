# Build Android (Capacitor + AdMob) — BERUANG

This app is a single-file web app (`index.html`) wrapped with Capacitor
to produce a native Android APK/AAB, with Google AdMob (banner + rewarded)
via `@capacitor-community/admob`.

- **App name:** BERUANG
- **App ID (package):** `com.altomedia.beruang`
- **Developer:** ALTOMEDIA (altomediaindonesia@gmail.com)
- **minSdk:** 23 (Android 6.0+) · **compileSdk / targetSdk:** 37

## Prerequisites

- Node.js 18+ and npm
- Java 21 (JDK) and `keytool`
- Android SDK: platform-tools, `platforms;android-37`, `build-tools;37.0.0`
  ( licences accepted via `sdkmanager --licenses`)
- ImageMagick 7 (only if regenerating launcher icons from `icon.png`)
- The AdMob IDs below are wired into `capacitor.config.json` (native init)
  and into the `ADMOB` module in `index.html`.

| Unit    | ID                                            |
|---------|-----------------------------------------------|
| App ID  | `ca-app-pub-6881903056221433~1794482255`      |
| Reward  | `ca-app-pub-6881903056221433/9174278828`      |
| Banner  | `ca-app-pub-6881903056221433/6548115489`      |

## Project layout

```
index.html              # the web app (source of truth)
icon.png                # 512x512 source icon
www/                    # copy of index.html + icon.png (webDir for Capacitor)
capacitor.config.json   # appId, appName, webDir, AdMob plugin config
android/                # native Android project (committed)
ALTOMEDIA/              # keystore, signed APK/AAB, store graphics, docs
```

## One-time setup (already done, for reference)

```bash
npm install
npm install -D typescript          # required by cap CLI tooling
mkdir -p www && cp index.html icon.png www/
npx cap add android
```

The `android/` folder **is** committed. Only generated build outputs
(`android/app/build/`, `android/.gradle/`, synced assets under
`android/app/src/main/assets/public/`) are git-ignored.

## Regenerate launcher icons

```bash
bash /tmp/genicons.sh   # uses ImageMagick to render all mipmap densities
```

## Build / sync after changes to index.html

```bash
cp index.html www/index.html        # keep webDir copy in sync
npx cap sync android                # copy web assets + apply plugin config
cd android
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # signed release APK
./gradlew :app:bundleRelease        # signed release AAB (for Google Play)
```

Outputs:
- `android/app/build/outputs/apk/release/app-release.apk`
- `android/app/build/outputs/bundle/release/app-release.aab`

## Signing

Release builds are signed automatically via `android/keystore.properties`,
which points at `ALTOMEDIA/ALTOMEDIA.jks`:

| Field          | Value             |
|----------------|-------------------|
| Keystore       | `ALTOMEDIA/ALTOMEDIA.jks` |
| Store password | `Kdsmedia@123`    |
| Key alias      | `kdsmedia`        |
| Key password   | `Kdsmedia@123`    |
| Validity       | 10000 days        |
| DN             | `CN=ALTOMEDIA, OU=Developer, O=ALTOMEDIA, L=Karawang, ST=Jawa Barat, C=ID` |

SHA-256 of the signing certificate:
`EB:E1:A0:A5:24:2E:9B:CA:9A:E8:57:EE:1D:A4:C6:CC:51:53:C6:91:0D:EC:52:E5:94:CA:7A:D3:5A:28:2D:AB`

Verify a signed APK:
```bash
$ANDROID_HOME/build-tools/37.0.0/apksigner verify app-release.apk
```

## How AdMob works in this app

- On native Android, `window.Capacitor.isNativeAvailable === true` and
  the `@capacitor-community/admob` plugin is present.
- The `ADMOB` module in `index.html` auto-detects this:
  - **Native:** real banner (bottom overlay) + real rewarded ads. The
    banner is shown contextually per view and hidden on the upload screen.
  - **Web (dev browser):** the AdMob calls are no-ops (no fake ads, no
    simulated rewards). Rewarded prompts simply resolve as cancelled so
    the UI can still be exercised without a device.

## Rewarded ad quota flow

When a user hits their daily post/comment limit (tier-based), they are
prompted to watch a rewarded ad. On a completed (no-skip) view, the app
grants +1 extra post OR +1 extra comment for that day, persisted in the
wallet `usage.adGrants` object. Master tier is unlimited and is never
prompted. Event listeners are cleaned up with `removeAllListeners()`
after each rewarded call, and the prompt promise resolves exactly once
(cancelled if the modal is dismissed).

