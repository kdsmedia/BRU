-- ============================================================
-- BERUANG — SETUP DATABASE LENGKAP (IDEMPOTENT)
-- ============================================================
-- File SQL ini membuat SELURUH struktur database yang dibutuhkan
-- aplikasi BERUANG. AMAN DIJALANKAN BERULANG KALI di SQL Editor
-- Supabase — tidak akan error duplikat.
--
-- Cara pakai:
--   1. Buka Dashboard Supabase → SQL Editor
--   2. Hapus semua isi query lama
--   3. Tempel SELURUH file ini
--   4. Klik Run
--
-- Isi:
--   A. Tabel `nodes`       — key-value tree (database inti aplikasi)
--   B. RPC `cas_update`    — transaksi atomik wallet (compare-and-swap)
--   C. Realtime            — aktifkan live update untuk nodes & AI logs
--   D. RLS `nodes`         — keamanan akses data
--   E. Storage bucket      — upload gambar (post, story, avatar)
--   F. Tabel AI            — ai_agents, ai_memory, ai_activity_logs
--   G. pg_cron             — jadwal otomatis AI engine tiap 5 menit
--
-- Project: https://jzyfxdysukzvnfllcbvq.supabase.co
-- ============================================================


-- ############################################################
-- A. TABEL NODES (database inti aplikasi)
-- ############################################################
-- Aplikasi BERUANG menyimpan SEMUA data (users, posts, wallets,
-- followers, notifications, stories, chats, dll) di satu tabel
-- key-value `nodes`, mirip Firebase Realtime Database. Setiap
-- "path" adalah lokasi data, mis:
--   users/{uid}           → { username, photo, role, is_ai, ... }
--   posts/{pid}           → { uid, caption, image, likes, ... }
--   wallets/{uid}/balance → angka poin
--   followers/{uid}       → { uid1: true, uid2: true, ... }
--   notifications/{uid}   → { notifId: { type, from, text, ts } }
--   private_chats/{chatId} → { msgId: { from, text, ts } }
--   stories/{sid}         → { uid, image, ts }
--   blocked/{uid}         → { targetUid: true }
--   account_index/{uid}   → nomor akun 6 digit

create table if not exists public.nodes (
    path  text   primary key,
    value jsonb,
    ts    bigint not null default 0
);

-- Index untuk query path LIKE 'prefix/%' (digunakan realtime & tree build)
create index if not exists nodes_path_like_idx
    on public.nodes (path text_pattern_ops);


-- ############################################################
-- B. RPC cas_update (transaksi atomik wallet)
-- ############################################################
-- Compare-and-swap: dipakai oleh runTransaction() di aplikasi
-- untuk update saldo wallet secara atomik (transfer poin).
-- Mengembalikan 1 jika sukses (ts cocok), 0 jika gagal (race).

create or replace function public.cas_update(
    p_path        text,
    p_expected_ts bigint,
    p_new_value   jsonb,
    p_new_ts      bigint
) returns int
language plpgsql
security definer
as $$
declare
    ok int;
begin
    -- Update hanya jika timestamp cocok (optimistic concurrency)
    update public.nodes
        set value = p_new_value, ts = p_new_ts
        where path = p_path and coalesce(ts, 0) = p_expected_ts
        returning 1 into ok;

    -- Kalau baris belum ada (expected_ts = 0), insert baru
    if ok is null and p_expected_ts = 0 then
        insert into public.nodes (path, value, ts)
            values (p_path, p_new_value, p_new_ts)
            on conflict (path) do nothing
            returning 1 into ok;
    end if;

    return coalesce(ok, 0);
end;
$$;

-- Beri izin agar user terautentikasi bisa memanggil RPC ini
grant execute on function public.cas_update to authenticated;


-- ############################################################
-- C. REALTIME (live update)
-- ############################################################
-- Aktifkan Supabase Realtime agar onValue() di aplikasi langsung
-- dapat update saat data berubah (feed baru, chat baru, notifikasi).
-- Pakai DO block agar aman dijalankan ulang (cek sebelum add).

do $$
begin
    -- nodes (data inti aplikasi)
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'nodes'
    ) then
        alter publication supabase_realtime add table public.nodes;
    end if;
end $$;


-- ############################################################
-- D. ROW LEVEL SECURITY (RLS) — tabel nodes
-- ############################################################
-- User terautentikasi (yang sudah login) bisa baca & tulis semua
-- node aplikasi. Data sensitif (PIN wallet, dll) dilindungi oleh
-- struktur path — setiap user hanya mengakses path miliknya via
-- UI. Tingkatkan kebijakan ini per-path untuk keamanan lebih ketat.

alter table public.nodes enable row level security;

-- Hapus policy lama jika ada (idempotent), lalu buat ulang
drop policy if exists "authed full access" on public.nodes;

create policy "authed full access"
    on public.nodes
    for all
    to authenticated
    using (true)
    with check (true);


-- ############################################################
-- E. STORAGE BUCKET "media"
-- ############################################################
-- Bucket public untuk upload gambar: postingan, story, avatar.
-- Aplikasi memakai sb.storage.from('media').

-- 1. Buat bucket public "media" (idempotent)
insert into storage.buckets (id, name, public)
    values ('media', 'media', true)
    on conflict (id) do nothing;

-- 2. Policy: siapa saja bisa baca (public)
drop policy if exists "media read" on storage.objects;
create policy "media read"
    on storage.objects
    for select
    using (bucket_id = 'media');

-- 3. Policy: user terautentikasi bisa upload
drop policy if exists "media upload" on storage.objects;
create policy "media upload"
    on storage.objects
    for insert
    to authenticated
    with check (bucket_id = 'media');

-- 4. Policy: user terautentikasi bisa update file miliknya
drop policy if exists "media update own" on storage.objects;
create policy "media update own"
    on storage.objects
    for update
    to authenticated
    using (bucket_id = 'media')
    with check (bucket_id = 'media');

-- 5. Policy: user terautentikasi bisa hapus file miliknya
drop policy if exists "media delete own" on storage.objects;
create policy "media delete own"
    on storage.objects
    for delete
    to authenticated
    using (bucket_id = 'media');


-- ############################################################
-- F. TABEL AI (metadata AI engine)
-- ############################################################
-- Tabel ini hanya diakses oleh Edge Function (service_role
-- bypass RLS). User biasa TIDAK bisa membacanya.

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

-- RLS: tabel AI backend-only. Tanpa policy => hanya service_role (bypass RLS).
alter table public.ai_agents        enable row level security;
alter table public.ai_memory        enable row level security;
alter table public.ai_activity_logs enable row level security;

-- Realtime untuk ai_activity_logs (dipindah ke sini karena tabelnya baru
-- dibuat di atas — tidak bisa add ke publication sebelum tabel ada).
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'ai_activity_logs'
    ) then
        alter publication supabase_realtime add table public.ai_activity_logs;
    end if;
end $$;


-- ############################################################
-- G. pg_cron — JADWAL OTOMATIS AI ENGINE
-- ############################################################
-- Edge Function ai-engine dipanggil otomatis tiap 5 menit oleh
-- pg_cron. Function sendiri memutuskan AI mana yang aktif dan
-- menerapkan cooldown/rate limit, jadi polling sering aman.

create extension if not exists pg_cron with schema extensions;

-- Hapus job lama jika sudah ada (idempotent — aman kalau belum ada)
select cron.unschedule('beruang-ai-engine');

-- Buat jadwal baru: panggil Edge Function ai-engine tiap 5 menit
select cron.schedule(
    'beruang-ai-engine',
    '*/5 * * * *',
    $$ select net.http_post(
         url := 'https://jzyfxdysukzvnfllcbvq.supabase.co/functions/v1/ai-engine',
         headers := jsonb_build_object(
             'Content-Type','application/json',
             'apikey','sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx',
             'Authorization','Bearer sb_publishable_DgATc8UqYXx8qneQC8fi3A_dF_ZT6Lx'
         ),
         body := jsonb_build_object('trigger','cron')
       ) $$
);


-- ############################################################
-- H. CATATAN PENTING (tidak bisa dilakukan via SQL)
-- ############################################################
-- 1. AUTH: Di Dashboard Supabase → Authentication → Providers
--    → Email, set "Confirm email" = OFF.
--    Ini WAJIB agar user bisa langsung login setelah daftar
--    (aplikasi pakai email sintetis dari nomor HP, mis:
--    08xxxxxxxxxx@beruang.phone).
--
-- 2. EDGE FUNCTION: Setelah SQL ini dijalankan, Anda masih perlu:
--    a. Set secrets di Dashboard → Edge Functions → Secrets:
--       - AI_API_KEY             = (key OpenAI Anda yang valid)
--       - AI_ENABLED             = true
--       - SUPABASE_URL           = https://jzyfxdysukzvnfllcbvq.supabase.co
--       - SUPABASE_SERVICE_ROLE_KEY = (service_role key dari Dashboard → Settings → API)
--    b. Deploy function: supabase functions deploy ai-engine
--    c. Buat AI users (Andi, Sari, Budi) lewat endpoint create_agent
--    Detail lengkap: lihat file PANDUAN_SETUP_AI.md
--
-- 3. service_role key BUKAN publishable key. Publishable key
--    (sb_publishable_...) untuk client, service_role key
--    (sb_secret_... atau JWT eyJ...) untuk server/Edge Function.
-- ============================================================

-- SELESAI. Semua struktur database BERUANG sudah siap.
