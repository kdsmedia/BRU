# BERUANG Native Android (app-native)

Migration of the BERUANG social app from Capacitor + HTML wrapper to **native Android Kotlin (Jetpack Compose)**.

## Package & Backend
- Application ID: `com.altomedia.beruang`
- Source root: `app/src/main/java/com/altomedia/beruang/`
- Supabase project: `jzyfxdysukzvnfllcbvq.supabase.co`
  - URL/anon key configured in `data/SupabaseProvider.kt`
  - Service-role key is held out-of-band (never commit secrets)
- Backend data model is the `nodes` table (key/path → JSON tree), accessed via `NodesRepository`.

## Build environment
- JDK 17/21 (Debian `openjdk-21-jdk` works: `/usr/lib/jvm/java-21-openjdk-amd64`)
- Android SDK at `/home/openhands/Android/Sdk`
  - `platforms;android-37.0`, `build-tools;35.0.0`, `platform-tools`, `cmdline-tools;latest`
- `local.properties` → `sdk.dir=/home/openhands/Android/Sdk` (NOT committed)
- AGP 8.13.2, Gradle 8.14.3, Kotlin 2.0.21, compileSdk/targetSdk 37, minSdk 23
- `gradle.properties`: `android.suppressUnsupportedCompileSdk=37.0`
- Debug build: `./gradlew :app:assembleDebug`
- Release build: `./gradlew :app:assembleRelease :app:bundleRelease`
  - Signing reads `../ALTOMEDIA/keystore.properties` (gitignored → create locally):
    `storeFile=ALTOMEDIA/ALTOMEDIA.jks`, alias `kdsmedia`; the `.jks` is tracked
  - Outputs: `app/build/outputs/apk/release/app-release.apk` and
    `app/build/outputs/bundle/release/app-release.aab`
  - Current: versionCode 12, versionName "2.4.4"

## Dependencies
- Supabase Kotlin SDK **3.0.0** (`postgrest-kt-android-debug`, `auth-kt`, `realtime-kt`, `storage-kt`) — has breaking API changes (see below)
- Jetpack Compose (Material3), Coil (AsyncImage), AndroidX Lifecycle/ViewModel
- Compose Compiler via Kotlin 2.0.21 compose plugin

## Supabase 3.0.0 breaking-API notes (verified against bytecode)
- `select(columns = ...)` is GONE → use `select(Columns.list("a", "b"))` (value class `Columns`).
- `upsert(rows, onConflict = "path")` DSL changed → `upsert(rows) { onConflict = "path" }`.
- `Auth.importSession()` no-arg is GONE → use `refreshCurrentSession()` to refresh the current session; the Auth plugin auto-restores persisted sessions on init.
- `postgresChangeFlow<T>(schema: String, filter)` — **schema is required (no default)** and is the DB schema (e.g. `"public"`), NOT an event name. The type parameter `T` (`PostgresAction.Insert` / `Update` / `Delete`) selects the event type. Passing "INSERT"/"UPDATE"/"DELETE" as the schema is a bug that silently breaks subscriptions.
- `RealtimeChannel.subscribe()` and `Realtime.removeChannel(channel)` are `suspend` — call from a coroutine scope.
- `NodeSubscription` holds a `MutableStateFlow<JsonElement?>` and exposes `stateFlow` for collection; `value` getter reads `flow.value`. `refreshNow()` re-reads on the scope.

## Architecture
- `data/` — repositories (SupabaseProvider, NodesRepository, PostRepository, AuthRepository, ChatRepository, WalletRepository, StorageRepository, RealtimeRepository, AdminRepository)
- `ui/` — Compose screens + ViewModels (auth, feed, chat, notif, wallet, profile, admin, components, theme)
- Realtime pattern: `RealtimeRepository.watch(path, scope)` returns a `NodeSubscription` that debounces (~120ms) and re-reads the full path on each Postgres-Change event (same end-to-end behavior as the JS adapter).
- ViewModels expose `StateFlow`; screens use `collectAsState()`.

## Status (current)
- ✅ `:app:assembleDebug` — BUILD SUCCESSFUL
- ✅ `:app:assembleRelease` + `:app:bundleRelease` signed with ALTOMEDIA.jks — BUILD SUCCESSFUL
- ✅ 2 bugs fixed in v2.4.3 (vc11): default-Android-logo splash flash (parent
  Theme.SplashScreen defaults windowSplashScreenAnimatedIcon to sym_def_app_icon —
  MUST set it explicitly to @mipmap/ic_launcher_foreground), tier downgrade removed
  (TierSheet lower tiers are "Terlampaui"/non-clickable; switchTier rejects any
  index below current — tiers are one-way up only)
- ✅ 6 bugs fixed in v2.4.2 (vc10): white screen after login (LoginScreen had its own
  AuthViewModel — share ONE instance from AppNavHost + `authReady` gate before NavHost),
  new-user account id (resilient register writes + self-heal in bootstrapAdminAndCheckBlock),
  chat opened behind the message-list overlay (MainScaffold now closes list / restores on back),
  force close on follow (side effects runCatching-wrapped), mutual follow shows "Teman",
  edit-profile email optional (synthetic @beruang.phone hidden), NodesRepository set()/remove()
  descendant delete LIKE pattern fixed ("$path/%" → "path/%") — deleteMessage/clearChat/deletePost work now
- ✅ 4 real bugs fixed in v2.4.1 (vc9): CAS txn path projection (wallet silent failures),
  duplicate comment points, realtime leaf Insert/Delete subscription, blocked-account on register

## Pitfalls learned
- `viewModel()` inside a `composable {}` destination resolves to the CURRENT
  NavBackStackEntry — always pass shared ViewModels down from AppNavHost.
- Exceptions from a `rememberCoroutineScope().launch {}` click handler crash the app
  (Main dispatcher) — wrap repository calls in runCatching + toast.
- Overlay ordering in MainScaffold: later composables render on top; opening a chat
  from the message list must also hide the list (chatFromList flag).

## Git
- Remote: `github.com/kdsmedia/BRU.git` (branch `main`)
- The original Capacitor project is preserved as a backup (zip) — do not delete.
- Commits are pushed immediately after each change per workflow.

## Notes
- The Capacitor wrapper project still lives in the repo root; `app-native/` is the native port.
- Use the free OpenAI version only for any AI-assisted work in this project.
