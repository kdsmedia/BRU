# Build Android (Capacitor + AdMob)

This app is a single-file web app (`index.html`) wrapped with Capacitor
to produce a native Android APK, with Google AdMob (banner + rewarded)
via `@capacitor-community/admob`.

## Prerequisites

- Node.js 18+
- Android Studio (with Android SDK 34+)
- The AdMob IDs below are already wired into `capacitor.config.ts`
  (for native init) and into the `ADMOB` module in `index.html`.

| Unit    | ID                                            |
|---------|-----------------------------------------------|
| App ID  | `ca-app-pub-6881903056221433~1794482255`      |
| Reward  | `ca-app-pub-6881903056221433/9174278828`      |
| Banner  | `ca-app-pub-6881903056221433/6548115489`      |

## One-time setup

```bash
npm install
npx cap add android
```

`cap add android` generates the `android/` folder. It is git-ignored
(see `.gitignore`) — every developer runs it locally.

## Build / sync after changes to index.html

```bash
npx cap sync android      # copy web assets + apply plugin config
npx cap open android      # open in Android Studio -> Build > Generate APK
```

## How AdMob works in this app

- On native Android, `window.Capacitor.isNativeAvailable === true` and
  the `@capacitor-community/admob` plugin is present.
- The `ADMOB` module in `index.html` auto-detects this:
  - **Native:** real banner (overlay bottom) + real rewarded ads.
  - **Web (dev browser):** graceful fallback — banner slots render as
    styled "Ad" placeholders, and the rewarded flow grants the bonus
    quota directly (so the quota mechanic can be tested without a device).

## Rewarded ad quota flow

When a user hits their daily post/comment limit (tier-based), they are
prompted to watch a rewarded ad. On a completed (no-skip) view, the app
grants +1 extra post OR +1 extra comment for that day, persisted in the
wallet `usage.adGrants` object. Master tier is unlimited and is never
prompted.
