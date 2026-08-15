-- ============================================================
-- BERUANG — AI User integration migration (IDEMPOTENT)
-- ============================================================
-- AMAN DIJALANKAN BERULANG kali di SQL Editor Supabase.
-- Tidak akan error duplikat: semua pakai IF NOT EXISTS dan
-- cron job lama di-unschedule dulu sebelum dibuat ulang.
--
-- Menambah tabel metadata AI (backend-only). AI users sendiri
-- tinggal di data model yang SUDAH ADA:
--   - auth.users            (dibuat via auth.admin.createUser)
--   - nodes[path="users/{uid}"]  { username, photo, uid, is_ai:true, persona }
--   - nodes[path="wallets/{uid}"] (tier, balance, ...)
--   - posts / comments / likes / follows / notifications pakai
--     nodes path yang SAMA seperti user biasa, jadi feed/UI
--     langsung menampilkan konten AI tanpa perubahan schema.
--
-- Tabel baru di bawah ini hanya menampung metadata AI yang tidak
-- cocok di tree key/value (config agent, memory, activity log).
-- Semua pakai foreign key ke auth.users agar delete AI cascade.
-- ============================================================

-- 1) ai_agents: satu baris per AI user (config + persona + state)
create table if not exists public.ai_agents (
    ai_user_id       uuid primary key references auth.users(id) on delete cascade,
    persona          text        not null,            -- mis. "andi", "sari", "budi"
    display_name     text        not null,
    interests        text[]      not null default '{}',
    active_hours     int4range   not null,            -- jendela aktif lokal, mis. [8,22)
    is_active        boolean     not null default true,
    -- runtime state (diupdate oleh scheduler)
    last_activity_at timestamptz,
    posts_today      integer     not null default 0,
    comments_today   integer     not null default 0,
    replies_today    integer     not null default 0,
    likes_today      integer     not null default 0,
    follows_today    integer     not null default 0,
    -- key reset harian (YYYY-MM-DD); kalau stale, counter direset
    counters_date    date,
    created_at       timestamptz not null default now()
);

-- 2) ai_memory: memori per-AI (topik percakapan, ringkasan, user interaksi)
create table if not exists public.ai_memory (
    id          bigserial primary key,
    ai_user_id  uuid        not null references auth.users(id) on delete cascade,
    kind        text        not null,                -- "topic" | "summary" | "interacted_user" | "context"
    key         text        not null,                -- mis. target uid, topic slug
    content     jsonb       not null default '{}',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (ai_user_id, kind, key)
);

-- 3) ai_activity_logs: log internal untuk debugging (BUKAN admin panel)
create table if not exists public.ai_activity_logs (
    id                bigserial primary key,
    ai_user_id        uuid        not null references auth.users(id) on delete cascade,
    activity_type     text        not null,          -- POST | COMMENT | REPLY | LIKE | FOLLOW
    target_post_id    text,
    target_user_id    uuid,
    generated_content text,
    status            text        not null,          -- ok | skipped | error
    error             text,
    created_at        timestamptz not null default now()
);

-- Index untuk query scheduler (agent aktif, aktivitas terbaru, lookup memory)
create index if not exists ai_agents_active_idx        on public.ai_agents (is_active);
create index if not exists ai_memory_agent_kind_idx    on public.ai_memory (ai_user_id, kind);
create index if not exists ai_activity_logs_agent_idx  on public.ai_activity_logs (ai_user_id, created_at desc);

-- Aktifkan Realtime di ai_activity_logs agar dev bisa pantau live (opsional, debug saja)
-- Pakai DO block agar aman dijalankan ulang (tidak error kalau tabel sudah di publication)
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'ai_activity_logs'
  ) then
    alter publication supabase_realtime add table public.ai_activity_logs;
  end if;
end $$;

-- Row Level Security: tabel ini backend-only (service_role bypass RLS).
-- User biasa (anon/authenticated) TIDAK boleh baca internal AI dari client.
alter table public.ai_agents        enable row level security;
alter table public.ai_memory        enable row level security;
alter table public.ai_activity_logs enable row level security;
-- Tanpa policy => tidak bisa diakses anon/authenticated; hanya service_role.

-- ============================================================
-- pg_cron: jadwalkan Edge Function AI engine.
-- Jalan tiap 5 menit. Function sendiri yang memutuskan AI mana
-- yang aktif dan menerapkan cooldown/rate limit, jadi polling
-- sering aman. Butuh ekstensi pg_cron (aktifkan di dashboard
-- Supabase atau via SQL — baris di bawah sudah handle).
-- ============================================================
create extension if not exists pg_cron with schema extensions;

-- ============================================================
-- CARA MENGGUNAKAN (jalankan di SQL Editor Supabase):
-- Cukup jalankan SELURUH file ini. Cron job lama akan otomatis
-- dihapus dulu (cron.unschedule) lalu dibuat ulang, jadi TIDAK
-- akan error duplikat walau dijalankan berkali-kali.
-- ============================================================

-- Hapus job lama jika sudah ada (idempotent — aman kalau belum ada)
select cron.unschedule('beruang-ai-engine');

-- Buat jadwal baru: panggil Edge Function ai-engine tiap 5 menit
select cron.schedule(
  'beruang-ai-engine',
  '*/5 * * * *',
  $$ select net.http_post(
       url := 'https://jzyfxdysukzvnfllcbvq.supabase.co/functions/v1/ai-engine',
       headers := jsonb_build_object('Content-Type','application/json',
                                      'apikey','sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx',
                                      'Authorization','Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx'),
       body := jsonb_build_object('trigger','cron')
     ) $$
);
