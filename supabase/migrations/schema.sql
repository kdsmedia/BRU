-- ================================================================
-- BERUANG / BRU - Supabase full database setup
-- ================================================================
-- Jalankan file ini sekali di Supabase SQL Editor.
-- File ini idempotent: aman dijalankan ulang.
--
-- Arsitektur aplikasi BRU:
--   auth.users  : autentikasi email/password (nomor HP dipetakan ke email sintetis)
--   public.nodes: seluruh data aplikasi dalam Firebase-style path/value tree
--   ai_*        : metadata internal untuk AI User; bukan tabel duplikat aplikasi
--
-- Path nodes yang dipakai aplikasi:
-- users/{uid}, wallets/{uid}, account_index/{uid}, posts/{post_id},
-- stories/{story_id}, followers/{uid}/{other_uid}, following/{uid}/{other_uid},
-- notifications/{uid}/{notification_id}, private_chats/{chat_id}/{message_id},
-- blocked/{uid}/{other_uid}, dan referral pada users/{uid}.
--
-- API key AI tidak disimpan di SQL. Simpan sebagai Supabase Edge Function secret.
-- ================================================================

-- ---------------------------------------------------------------
-- 1. Extensions
-- ---------------------------------------------------------------
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema extensions;

-- ---------------------------------------------------------------
-- 2. Core key/value tree used by the existing mobile app
-- ---------------------------------------------------------------
create table if not exists public.nodes (
  path text primary key,
  value jsonb,
  ts bigint not null default 0
);

create index if not exists nodes_path_like_idx
  on public.nodes (path text_pattern_ops);

alter table public.nodes enable row level security;

drop policy if exists "bru authenticated nodes access" on public.nodes;
create policy "bru authenticated nodes access"
  on public.nodes
  for all
  to authenticated
  using (true)
  with check (true);

-- The frontend uses this RPC for atomic wallet balance changes.
create or replace function public.cas_update(
  p_path text,
  p_expected_ts bigint,
  p_new_value jsonb,
  p_new_ts bigint
) returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  updated_count integer;
begin
  if p_path is null or length(trim(p_path)) = 0 then
    raise exception 'path is required';
  end if;

  update public.nodes
     set value = p_new_value,
         ts = p_new_ts
   where path = p_path
     and coalesce(ts, 0) = coalesce(p_expected_ts, 0);

  get diagnostics updated_count = row_count;
  if updated_count = 1 then
    return 1;
  end if;

  if coalesce(p_expected_ts, 0) = 0 then
    insert into public.nodes(path, value, ts)
    values (p_path, p_new_value, p_new_ts)
    on conflict (path) do nothing;
    get diagnostics updated_count = row_count;
    if updated_count = 1 then
      return 1;
    end if;
  end if;

  return 0;
end;
$$;

revoke all on function public.cas_update(text, bigint, jsonb, bigint) from public;
grant execute on function public.cas_update(text, bigint, jsonb, bigint) to authenticated;

-- ---------------------------------------------------------------
-- 3. Realtime for feed, chat, notifications, wallet and stories
-- ---------------------------------------------------------------
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
     where pubname = 'supabase_realtime'
       and schemaname = 'public'
       and tablename = 'nodes'
  ) then
    alter publication supabase_realtime add table public.nodes;
  end if;
end;
$$;

-- ---------------------------------------------------------------
-- 4. Media storage used for posts, stories and avatars
-- ---------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('media', 'media', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "bru media read" on storage.objects;
create policy "bru media read"
  on storage.objects for select
  using (bucket_id = 'media');

drop policy if exists "bru media upload" on storage.objects;
create policy "bru media upload"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'media');

drop policy if exists "bru media update" on storage.objects;
create policy "bru media update"
  on storage.objects for update to authenticated
  using (bucket_id = 'media')
  with check (bucket_id = 'media');

drop policy if exists "bru media delete" on storage.objects;
create policy "bru media delete"
  on storage.objects for delete to authenticated
  using (bucket_id = 'media');

-- ---------------------------------------------------------------
-- 5. AI User metadata (uses auth.users and the existing nodes tree)
-- ---------------------------------------------------------------
create table if not exists public.ai_agents (
  ai_user_id uuid primary key references auth.users(id) on delete cascade,
  persona text not null,
  display_name text not null,
  interests text[] not null default '{}',
  active_hours int4range not null,
  is_active boolean not null default true,
  last_activity_at timestamptz,
  posts_today integer not null default 0,
  comments_today integer not null default 0,
  replies_today integer not null default 0,
  likes_today integer not null default 0,
  follows_today integer not null default 0,
  counters_date date,
  created_at timestamptz not null default now(),
  constraint ai_agents_nonnegative_counters check (
    posts_today >= 0 and comments_today >= 0 and replies_today >= 0
    and likes_today >= 0 and follows_today >= 0
  )
);

create table if not exists public.ai_memory (
  id bigint generated by default as identity primary key,
  ai_user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('topic', 'summary', 'interacted_user', 'context')),
  key text not null,
  content jsonb not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (ai_user_id, kind, key)
);

create table if not exists public.ai_activity_logs (
  id bigint generated by default as identity primary key,
  ai_user_id uuid not null references auth.users(id) on delete cascade,
  activity_type text not null check (activity_type in ('POST', 'COMMENT', 'REPLY', 'LIKE', 'FOLLOW')),
  target_post_id text,
  target_user_id uuid references auth.users(id) on delete set null,
  generated_content text,
  status text not null check (status in ('ok', 'skipped', 'error')),
  error text,
  created_at timestamptz not null default now()
);

create index if not exists ai_agents_active_idx
  on public.ai_agents (is_active, last_activity_at);
create index if not exists ai_memory_agent_kind_idx
  on public.ai_memory (ai_user_id, kind);
create index if not exists ai_activity_logs_agent_idx
  on public.ai_activity_logs (ai_user_id, created_at desc);
create index if not exists ai_activity_logs_target_idx
  on public.ai_activity_logs (target_post_id, activity_type, created_at desc);

create or replace function public.bru_set_ai_memory_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists bru_ai_memory_updated_at on public.ai_memory;
create trigger bru_ai_memory_updated_at
before update on public.ai_memory
for each row execute function public.bru_set_ai_memory_updated_at();

alter table public.ai_agents enable row level security;
alter table public.ai_memory enable row level security;
alter table public.ai_activity_logs enable row level security;
-- Deliberately no client policies: only the Edge Function service_role can access AI internals.

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
     where pubname = 'supabase_realtime'
       and schemaname = 'public'
       and tablename = 'ai_activity_logs'
  ) then
    alter publication supabase_realtime add table public.ai_activity_logs;
  end if;
end;
$$;

-- ---------------------------------------------------------------
-- 6. Automatic AI scheduler
-- ---------------------------------------------------------------
-- verify_jwt=false is configured in supabase/config.toml, so this request
-- does not expose a publishable or service_role key in the database.
-- The Edge Function still keeps its AI provider key server-side.
select cron.unschedule('bru-ai-engine')
where exists (select 1 from cron.job where jobname = 'bru-ai-engine');

select cron.schedule(
  'bru-ai-engine',
  '*/5 * * * *',
  $$
    select net.http_post(
      url := 'https://jzyfxdysukzvnfllcbvq.supabase.co/functions/v1/ai-engine',
      headers := jsonb_build_object('Content-Type', 'application/json'),
      body := jsonb_build_object('trigger', 'cron')
    )
  $$
);

-- ================================================================
-- Setelah SQL selesai:
-- 1. Deploy supabase/functions/ai-engine.
-- 2. Set secrets AI_ENABLED, AI_API_KEY, AI_API_BASE, AI_MODEL,
--    SUPABASE_URL, dan SUPABASE_SERVICE_ROLE_KEY di Edge Functions.
-- 3. Panggil action create_agent untuk persona andi, sari, dan budi.
-- 4. Matikan AI kapan saja dengan AI_ENABLED=false.
-- ================================================================
