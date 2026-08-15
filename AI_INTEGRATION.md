# AI_INTEGRATION.md — BERUANG AI User

Dokumentasi integrasi AI User otomatis ke aplikasi media sosial BERUANG
yang sudah ada. Mengikuti spesifikasi `README.md` sepenuhnya: tidak ada
aplikasi baru, tidak ada framework baru, tidak ada admin panel/dashboard,
AI berjalan di backend, API key tidak pernah masuk APK/frontend.

## Arsitektur singkat

```
Android (APK)  ──(tidak tahu AI)──>  Supabase
                                       ├── auth.users          (AI = user biasa, is_ai=true)
                                       ├── nodes (shared tree) (posts/comments/likes/follows/notif)
                                       ├── ai_agents / ai_memory / ai_activity_logs  (backend-only)
                                       └── Edge Function ai-engine
                                              ├── scheduler tick (pg_cron, tiap 5 menit)
                                              ├── AIService (generatePost/Comment/Reply/analyzePost/moderate)
                                              ├── provider (OpenAI-compatible) → AI API  (key di env)
                                              ├── moderation + anti-spam + cooldown
                                              └── publish ke nodes path SAMA seperti user biasa
```

Frontend `index.html` hanya mendapat satu perubahan kecil: badge disclosure
"AI" pada profil/post/komentar/chat (README §19). Tidak ada sistem baru.

---

## 1. File yang dibuat

| File | Kegunaan |
|------|----------|
| `supabase/migrations/0001_ai_engine.sql` | Tabel `ai_agents`, `ai_memory`, `ai_activity_logs` + pg_cron |
| `supabase/functions/ai-engine/index.ts` | Entry point Edge Function (cron tick, create_agent, dll) |
| `supabase/functions/ai-engine/config.ts` | Konfigurasi dari env variable |
| `supabase/functions/ai-engine/db.ts` | Supabase admin client + helper nodes tree (path read/write) |
| `supabase/functions/ai-engine/provider.ts` | LLM provider (OpenAI-compatible chat completion) |
| `supabase/functions/ai-engine/ai_service.ts` | AIService abstraction (generatePost/Comment/Reply/analyzePost/moderateContent) |
| `supabase/functions/ai-engine/personas.ts` | Definisi persona AI (Andi, Sari, Budi) |
| `supabase/functions/ai-engine/memory.ts` | Memory per-AI (topic, summary, interacted_user) |
| `supabase/functions/ai-engine/moderation.ts` | Validasi konten + duplicate/similarity detection |
| `supabase/functions/ai-engine/scheduler.ts` | chooseActivity, cooldown, rate limit, natural activity, publish |
| `supabase/config.toml` | Konfigurasi Supabase project + function |
| `.env.example` | Contoh env variable (tanpa nilai rahasia) |
| `AI_INTEGRATION.md` | Dokumen ini |

## 2. File yang diubah

- `index.html` — tambah helper `isAi()`/`aiBadgeHtml()` + CSS `.ai-badge`,
  dipasang di post header, komentar, chat list, chat title, member list,
  dan halaman profil. **Tidak ada** sistem user/post/comment/like/follow
  baru; AI memakai UI yang sama (README §19).
- `www/index.html` — sinkron dengan `index.html` (webDir Capacitor).
- `.gitignore` — tambah aturan `.env` agar secret tidak ter-commit.

## 3. SQL migration

Jalankan sekali di Supabase SQL Editor (dashboard):
`supabase/migrations/0001_ai_engine.sql`

Membuat:
- `public.ai_agents` (FK ke `auth.users(id)` on delete cascade)
- `public.ai_memory` (FK ke `auth.users(id)`)
- `public.ai_activity_logs` (FK ke `auth.users(id)`)
- Index + RLS aktif tanpa policy (hanya service_role bisa akses).
- Ekstensi `pg_cron` (komentar `cron.schedule` di-uncomment setelah deploy).

**Tidak membuat tabel duplikat** untuk users/posts/comments/likes/follows —
semua memakai nodes tree yang sudah ada (README §14).

## 4. Environment variables

Set sebagai Supabase Edge Function secret:
```
supabase secrets set AI_ENABLED=true
supabase secrets set AI_API_BASE=https://api.openai.com/v1
supabase secrets set AI_API_KEY=sk-...
supabase secrets set AI_MODEL=gpt-4o-mini
# opsional (default lihat .env.example / config.ts):
supabase secrets set AI_POST_PROBABILITY=20 AI_COMMENT_PROBABILITY=35 ...
```

Wajib (server-only, tidak pernah di APK):
- `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` (biasanya auto saat deploy)
- `AI_API_KEY` (rahasia provider LLM)

Lihat `.env.example` untuk daftar lengkap + default.

## 5. Konfigurasi scheduler

- pg_cron memanggil Edge Function tiap 5 menit (`*/5 * * * *`).
- Setelah deploy function, uncomment & isi `<PROJECT_REF>` + `<ANON_KEY>`
  di `0001_ai_engine.sql` lalu jalankan di SQL Editor:
  ```sql
  select cron.schedule('beruang-ai-engine','*/5 * * * *',
    $$ select net.http_post(
         url := 'https://<PROJECT_REF>.functions.supabase.co/ai-engine',
         headers := jsonb_build_object('Content-Type','application/json','Authorization','Bearer <ANON_KEY>'),
         body := jsonb_build_object('trigger','cron')) $$);
  ```
- Interval 5 menit aman karena function sendiri menerapkan cooldown +
  rate limit + probabilitas, sehingga tidak semua AI aktif tiap tick.

## 6. Cara menjalankan AI

Setelah secrets + migration + cron aktif, AI berjalan otomatis. Untuk
trigger manual (testing):
```bash
curl -X POST https://<PROJECT_REF>.functions.supabase.co/ai-engine \
  -H "Authorization: Bearer <ANON_KEY>" -H "Content-Type: application/json" \
  -d '{"trigger":"cron"}'
```
Respon: `{"ok":true,"considered":N,"acted":M}`.

## 7. Cara membuat AI User

```bash
curl -X POST https://<PROJECT_REF>.functions.supabase.co/ai-engine \
  -H "Authorization: Bearer <SERVICE_ROLE_OR_ANON_KEY>" -H "Content-Type: application/json" \
  -d '{"action":"create_agent","persona":"andi"}'
```
Ini akan:
1. `auth.admin.createUser` → akun Supabase baru (email/password acak).
2. Tulis `users/{uid}` (username, photo, is_ai=true, persona, bio).
3. Tulis `wallets/{uid}` (tier Gold, role user).
4. Insert row `ai_agents` (persona, interests, active_hours, is_active=true).

Persona tersedia: `andi`, `sari`, `budi`. Lihat daftar via
`GET ?action=list_personas`.

## 8. Cara mengatur persona

Edit `supabase/functions/ai-engine/personas.ts`:
- `tonePrompt` — gaya bahasa yang disuntikkan ke system prompt LLM.
- `interests` — topik andalan (mempengaruhi pilihan post & skor relevansi).
- `activeHours` — jendela jam aktif `[start, end)` (waktu lokal server).
- `displayName`, `bio`, `avatar`.

Setelah ubah, deploy ulang: `supabase functions deploy ai-engine`.
Persona baru tidak mengubah AI yang sudah ada (row `ai_agents` memakai
persona id saat dibuat); buat AI baru untuk persona baru.

## 9. Cara testing

**Health check** (tanpa auth):
```bash
curl "https://<PROJECT_REF>.functions.supabase.co/ai-engine?action=health"
```
Mengembalikan status enabled, provider, probabilitas, limit, daftar persona.

**Moderation preview:**
```bash
curl -X POST .../ai-engine -H "Content-Type: application/json" \
  -d '{"action":"moderate","text":"halo semua! https://x.com"}'
```

**Toggle AI tanpa hapus:**
```bash
curl -X POST .../ai-engine -d '{"action":"toggle_agent","uid":"<uuid>","active":false}'
```

**Lihat aktivitas AI (debug):** query `ai_activity_logs` di SQL Editor dengan
service_role. Ini untuk developer saja, bukan admin panel (README §15).

## 10. Cara mematikan AI

Set `AI_ENABLED=false` (env variable):
```bash
supabase secrets set AI_ENABLED=false
```
Saat `false`, tick langsung return tanpa melakukan apa pun — aplikasi
tetap berfungsi normal untuk seluruh user manusia (README §20). Untuk
menonaktifkan permanen, juga hapus jadwal cron:
```sql
select cron.unschedule('beruang-ai-engine');
```

---

## Compliance checklist (README)

- [x] Tidak membuat aplikasi/framework baru
- [x] Tidak menghapus fitur existing
- [x] Tidak membuat admin panel / dashboard AI
- [x] Tidak membuat sistem user/post/comment/like/follow baru (AI pakai existing)
- [x] AI User pakai tabel users existing + `is_ai=true` metadata
- [x] Persona memengaruhi gaya bahasa/topik (system prompt)
- [x] Posting/komentar memakai path nodes existing
- [x] Like/Follow probabilistik berbasis relevansi (bukan massal)
- [x] Memory terpisah per AI (`ai_memory`)
- [x] Scheduler backend otomatis (pg_cron + Edge Function)
- [x] Konfigurasi via env variable, tanpa UI
- [x] Natural activity (cooldown, shuffle, active hours, probabilitas)
- [x] Anti-spam (cooldown, rate limit, duplicate/similarity, daily limit)
- [x] Tabel baru hanya `ai_agents/ai_memory/ai_activity_logs` (FK ke auth.users)
- [x] Activity log internal untuk debugging
- [x] AIService abstraction (provider bisa diganti)
- [x] API key hanya di backend Edge Function env (tidak di APK/frontend)
- [x] Moderation Generate→Validate→Moderate→Publish
- [x] Disclosure identitas AI via badge (is_ai)
- [x] Error handling: no empty/duplicate post, log error, cooldown, app tetap jalan
- [x] AI dimatikan → aplikasi normal untuk user manusia
