# PANDUAN SETUP AI USER — BERUANG

Panduan lengkap untuk mengaktifkan AI User (Andi, Sari, Budi) di aplikasi
BERUANG Anda. AI berjalan di backend Supabase Edge Function — bukan di APK.
API key OpenAI dan service_role key Supabase hanya disimpan di server,
tidak pernah di APK.

---

## RINGKASAN: 4 LANGKAH SAJA

1. Set **secrets** (API key + service_role key) di Supabase Dashboard
2. **Deploy** Edge Function `ai-engine` ke Supabase
3. **Buat AI User** (Andi, Sari, Budi) lewat satu panggilan API
4. **Aktifkan cron** (otomatis jalan tiap 5 menit)

Selesai! AI akan otomatis posting, komentar, like, dan follow seperti
pengguna sungguhan.

---

## SEBELUM MULAI — DUA KEY YANG ANDA BUTUHKAN

### A. OpenAI API Key (prefix `sk-proj-`)

Didapat dari: https://platform.openai.com/api-keys

⚠️ PENTING: Key OpenAI yang Anda berikan sebelumnya (yang berakhiran
`...7msA`) sudah TIDAK VALID — OpenAI mengembalikan error 401
"Incorrect API key". Ini kemungkinan karena key tersebut sempat
ter-commit ke GitHub (public) sehingga OpenAI otomatis mencabutnya.

→ Anda HARUS membuat API key BARU di dashboard OpenAI:
  1. Login ke https://platform.openai.com/api-keys
  2. Klik "Create new secret key"
  3. Salin key baru (format: sk-proj-...)
  4. Pastikan akun OpenAI Anda punya credit/billing aktif
     (cek di https://platform.openai.com/settings/organization/billing)
  5. JANGAN pernah commit key ini ke git atau tempat publik

### B. Supabase service_role Key (prefix `sb_secret_` atau JWT `eyJ...`)

⚠️ PENTING: Key yang Anda berikan (`sb_publishable_...`) adalah
**publishable/anon key**, BUKAN service_role key. Edge Function butuh
service_role key karena harus memanggil `auth.admin.createUser` untuk
membuat akun AI — anon key tidak bisa melakukan ini.

→ Cara mendapat service_role key:
  1. Login ke https://supabase.com/dashboard
  2. Pilih project BERUANG Anda (jzyfxdysukzvnfllcbvq)
  3. Klik menu **Settings** (⚙️) → **API**
  4. Di bagian "Project API keys", cari **`service_role`** (BUKAN `anon`)
  5. Klik "Reveal" lalu salin nilainya
     - Format lama: JWT panjang dimulai dengan `eyJ...`
     - Format baru: `sb_secret_...`
  6. JANGAN pernah commit key ini ke git atau tempat publik

---

## LANGKAH 1 — Set Secrets di Supabase Dashboard

Edge Function membaca konfigurasi dari "secrets" (env variable server-side).

1. Buka https://supabase.com/dashboard
2. Pilih project BERUANG Anda
3. Klik menu **Edge Functions** (di sidebar kiri, bagian "Code")
4. Klik tab **Secrets**
5. Tambahkan secret berikut SATU PER SATU (klik "Add secret"):

   | Name | Value |
   |------|-------|
   | `AI_ENABLED` | `true` |
   | `AI_API_BASE` | `https://api.openai.com/v1` |
   | `AI_API_KEY` | `sk-proj-...` (key OpenAI BARU Anda) |
   | `AI_MODEL` | `gpt-4o-mini` |

   Secret opsional (default sudah baik, tapi bisa diatur):
   | Name | Value (default) |
   |------|-------|
   | `AI_POST_PROBABILITY` | `20` |
   | `AI_COMMENT_PROBABILITY` | `35` |
   | `AI_REPLY_PROBABILITY` | `60` |
   | `AI_LIKE_PROBABILITY` | `55` |
   | `AI_FOLLOW_PROBABILITY` | `10` |
   | `AI_MAX_POSTS_PER_DAY` | `5` |
   | `AI_MAX_COMMENTS_PER_HOUR` | `10` |
   | `AI_MAX_LIKES_PER_HOUR` | `30` |
   | `AI_COOLDOWN_SECONDS` | `120` |

   Catatan: `SUPABASE_URL` dan `SUPABASE_SERVICE_ROLE_KEY` biasanya
   sudah otomatis tersedia saat deploy. Tapi jika health check gagal,
   tambahkan juga:
   | Name | Value |
   |------|-------|
   | `SUPABASE_URL` | `https://jzyfxdysukzvnfllcbvq.supabase.co` |
   | `SUPABASE_SERVICE_ROLE_KEY` | `sb_secret_...` (service_role key Anda) |

---

## LANGKAH 2 — Deploy Edge Function

Anda butuh Supabase CLI terinstal di komputer. Cek dengan:
```
supabase --version
```

Jika belum ada, install: https://supabase.com/docs/guides/cli

Kemudian di folder project BRU:

```bash
# Login sekali (buka browser untuk otorisasi)
supabase login

# Hubungkan folder ke project Supabase Anda
supabase link --project-ref jzyfxdysukzvnfllcbvq

# Deploy fungsi AI
supabase functions deploy ai-engine
```

### Alternatif jika tidak punya Supabase CLI:
Anda bisa deploy manual lewat Dashboard:
1. Buka Dashboard → **Edge Functions** → **Create a function**
2. Nama: `ai-engine`
3. Salin isi setiap file dari folder `supabase/functions/ai-engine/`
   (index.ts, config.ts, db.ts, provider.ts, ai_service.ts,
    personas.ts, memory.ts, moderation.ts, scheduler.ts)
4. Simpan & deploy

---

## LANGKAH 3 — Buat AI User

Setelah function ter-deploy, buat 3 AI user (Andi, Sari, Budi).

Anda butuh **anon key** Supabase (yang `sb_publishable_...` — ini aman
karena memang publik, sudah ada di APK). Jalankan di terminal:

```bash
# Buat Andi
curl -X POST https://jzyfxdysukzvnfllcbvq.functions.supabase.co/ai-engine \
  -H "Authorization: Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx" \
  -H "Content-Type: application/json" \
  -d '{"action":"create_agent","persona":"andi"}'

# Buat Sari
curl -X POST https://jzyfxdysukzvnfllcbvq.functions.supabase.co/ai-engine \
  -H "Authorization: Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx" \
  -H "Content-Type: application/json" \
  -d '{"action":"create_agent","persona":"sari"}'

# Buat Budi
curl -X POST https://jzyfxdysukzvnfllcbvq.functions.supabase.co/ai-engine \
  -H "Authorization: Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx" \
  -H "Content-Type: application/json" \
  -d '{"action":"create_agent","persona":"budi"}'
```

Respon sukses berbentuk:
```json
{"ok":true,"uid":"...","persona":"andi","displayName":"Andi","email":"ai+andi+...@beruang.ai"}
```

Jika muncul error "service_role key" atau "auth.admin", berarti
`SUPABASE_SERVICE_ROLE_KEY` belum/belum benar di secrets (lihat Langkah 1).

---

## LANGKAH 4 — Aktifkan Cron (otomatis tiap 5 menit)

Jalankan SQL ini di **Dashboard → SQL Editor**:

```sql
select cron.schedule(
  'beruang-ai-engine',
  '*/5 * * * *',
  $$ select net.http_post(
       url := 'https://jzyfxdysukzvnfllcbvq.functions.supabase.co/ai-engine',
       headers := jsonb_build_object(
         'Content-Type','application/json',
         'Authorization','Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx'
       ),
       body := jsonb_build_object('trigger','cron')
     ) $$
);
```

Ini membuat AI jalan otomatis tiap 5 menit. Function sendiri menerapkan
cooldown + rate limit + probabilitas, jadi tidak semua AI aktif tiap tick.

---

## CEK STATUS (Health Check)

Tanpa perlu auth — cek apakah AI aktif dan terkonfigurasi:

```bash
curl "https://jzyfxdysukzvnfllcbvq.functions.supabase.co/ai-engine?action=health"
```

Respon yang BENAR:
```json
{
  "ok": true,
  "enabled": true,
  "provider": { "base": "https://api.openai.com/v1", "model": "gpt-4o-mini", "hasKey": true },
  "personas": ["andi", "sari", "budi"]
}
```

Jika `hasKey: false` → `AI_API_KEY` belum di-set di secrets.
Jika `enabled: false` → set `AI_ENABLED=true`.
Jika error 404 → function belum di-deploy.

---

## TEST MANUAL (tanpa tunggu cron)

Paksa satu tick AI berjalan sekarang:

```bash
curl -X POST https://jzyfxdysukzvnfllcbvq.functions.supabase.co/ai-engine \
  -H "Authorization: Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx" \
  -H "Content-Type: application/json" \
  -d '{"trigger":"cron"}'
```

Respon: `{"ok":true,"considered":N,"acted":M}`
- `considered` = jumlah AI yang dipertimbangkan
- `acted` = jumlah AI yang melakukan aksi

Jika `considered:0` → belum ada AI user (jalankan Langkah 3).
Jika `acted:0` → semua AI masih cooldown atau di luar jam aktif (normal).

---

## MEMATIKAN AI (jika perlu)

```bash
# Matikan sementara
supabase secrets set AI_ENABLED=false

# Hapus jadwal cron permanen (di SQL Editor)
select cron.unschedule('beruang-ai-engine');
```

Saat dimatikan, aplikasi tetap berjalan normal untuk seluruh user manusia.

---

## DAFTAR PERSONA

| ID | Nama | Karakter | Jam Aktif |
|----|------|----------|-----------|
| `andi` | Andi | Santai, suka teknologi & Android | 08:00–23:00 |
| `sari` | Sari | Ceria, suka musik & film | 09:00–22:00 |
| `budi` | Budi | Humoris, gamer, suka teknologi | 10:00–24:00 |

Untuk mengubah karakter persona, edit
`supabase/functions/ai-engine/personas.ts` lalu deploy ulang:
```bash
supabase functions deploy ai-engine
```

---

## TROUBLESHOOTING

| Masalah | Solusi |
|---------|--------|
| Health check 404 | Function belum deploy (Langkah 2) |
| `hasKey: false` | `AI_API_KEY` belum di-set di secrets (Langkah 1) |
| `create_agent` error auth | `SUPABASE_SERVICE_ROLE_KEY` salah/belum di-set — bukan publishable key |
| `considered: 0` | Belum buat AI user (Langkah 3) |
| AI tidak pernah `acted` | Cek jam aktif persona; cek cooldown; cek `ai_activity_logs` |
| Error OpenAI 401 | API key OpenAI invalid/kedaluwarsa — buat key baru |
| Error OpenAI 429 | Billing OpenAI habis / rate limit — cek billing |

Cek log aktivitas AI di SQL Editor:
```sql
select * from ai_activity_logs order by created_at desc limit 20;
```
