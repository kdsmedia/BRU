# AGENTS.md — Base44 dev environment for BERUANG

## What this project is
BERUANG is a social media app by ALTOMEDIA. The **current codebase** is a
native Android app (`app-native/`, Kotlin / Jetpack Compose) backed by a
Supabase cloud project (`jzyfxdysukzvnfllcbvq.supabase.co`). A native Android
app **cannot** run in a web browser preview.

The only web-previewable artifact is the **older Capacitor/HTML single-page
app** preserved in `capacitor-backup.zip`, extracted to `web/www/`. It is
hand-written source (no build step) and is what `docker-compose.base44.yml`
serves.

## How the preview works
- `docker-compose.base44.yml` runs `nginx:alpine`, bind-mounts `web/www/` as
  the document root, and maps host port **3000 → 80**.
- `web/www/index.html` is a self-contained SPA (~180 KB, inline CSS + JS) that
  loads the Supabase JS SDK from a CDN and connects to the real Supabase cloud
  project using an embedded **publishable anon key** (safe for the frontend).
- No build step, no node_modules, no external secrets required for the
  preview. Edits to `web/www/index.html` are picked up on browser refresh
  (nginx serves the file directly); call `reload_preview` to force a refresh.
- nginx config (`nginx.base44.conf`) serves any unknown path back to
  `index.html` (SPA fallback) and adds a permissive CORS header.

## Secrets
- The web frontend needs **no** secrets — the Supabase URL + publishable anon
  key are embedded in `index.html`.
- The Supabase Edge Function (`supabase/functions/ai-engine/`, Deno) needs
  `AI_API_KEY` (Groq, free tier) and `SUPABASE_SERVICE_ROLE_KEY`, but it is
  deployed to Supabase cloud — it is **not** run locally and is not part of the
  preview. Do not put these keys in the APK/frontend.

## Verifying the app
- `docker compose -f docker-compose.base44.yml up -d` then
  `curl -sf http://localhost:3000/` → HTTP 200, `<title>BERUANG - Social</title>`.
- The preview shows the BERUANG login screen (Masuk / Daftar tabs, phone +
  password fields). Logging in hits the real Supabase auth endpoint.

## Native Android build (not used by the preview)
See `app-native/AGENTS.md` for the full native build environment (JDK 21,
Android SDK, Gradle). It produces APKs/AABs and is unrelated to the web
preview.
