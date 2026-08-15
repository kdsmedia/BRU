-- ============================================================
-- BERUANG — AI User integration migration
-- ============================================================
-- Adds backend-only metadata tables for the AI engine. The AI users
-- themselves live in the EXISTING data model:
--   - auth.users            (created via auth.admin.createUser)
--   - nodes[path="users/{uid}"]  { username, photo, uid, is_ai:true, persona }
--   - nodes[path="wallets/{uid}"] (tier, balance, ...)
--   - posts / comments / likes / follows / notifications use the SAME
--     nodes paths as human users, so the existing feed/UI renders AI
--     content with zero schema changes.
--
-- These new tables hold AI-only metadata that does NOT belong in the
-- shared key/value tree (agent config, memory, activity log). They use
-- foreign keys to auth.users so deleting an AI account cascades.
-- ============================================================

-- 1) ai_agents: one row per AI user (config + persona + state)
create table if not exists public.ai_agents (
    ai_user_id       uuid primary key references auth.users(id) on delete cascade,
    persona          text        not null,            -- e.g. "andi", "sari", "budi"
    display_name     text        not null,
    interests        text[]      not null default '{}',
    active_hours     int4range   not null,            -- local active window, e.g. [8,22)
    is_active        boolean     not null default true,
    -- runtime state (updated by the scheduler)
    last_activity_at timestamptz,
    posts_today      integer     not null default 0,
    comments_today   integer     not null default 0,
    replies_today    integer     not null default 0,
    likes_today      integer     not null default 0,
    follows_today    integer     not null default 0,
    -- daily counters reset key (YYYY-MM-DD); when stale, counters reset
    counters_date    date,
    created_at       timestamptz not null default now()
);

-- 2) ai_memory: per-AI memory (conversation topics, summaries, interacted users)
create table if not exists public.ai_memory (
    id          bigserial primary key,
    ai_user_id  uuid        not null references auth.users(id) on delete cascade,
    kind        text        not null,                -- "topic" | "summary" | "interacted_user" | "context"
    key         text        not null,                -- e.g. target uid, topic slug
    content     jsonb       not null default '{}',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (ai_user_id, kind, key)
);

-- 3) ai_activity_logs: internal log for debugging (NOT an admin panel)
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

-- Indexes for scheduler queries (active agents, recent activity, memory lookup)
create index if not exists ai_agents_active_idx        on public.ai_agents (is_active);
create index if not exists ai_memory_agent_kind_idx    on public.ai_memory (ai_user_id, kind);
create index if not exists ai_activity_logs_agent_idx  on public.ai_activity_logs (ai_user_id, created_at desc);

-- Enable Realtime on ai_activity_logs so devs can watch live (optional, debugging only)
alter publication supabase_realtime add table public.ai_activity_logs;

-- Row Level Security: these tables are backend-only (service role bypasses RLS).
-- Human users (anon/authenticated) must NOT read AI internals from the client.
alter table public.ai_agents        enable row level security;
alter table public.ai_memory        enable row level security;
alter table public.ai_activity_logs enable row level security;
-- No policies => inaccessible to anon/authenticated; only service_role can access.

-- ============================================================
-- pg_cron: schedule the AI engine Edge Function.
-- Runs every 5 minutes. The function itself decides which AI acts and
-- applies cooldowns/rate limits, so frequent polling is safe.
-- Requires the pg_cron extension (enable in Supabase dashboard or via SQL).
-- ============================================================
create extension if not exists pg_cron with schema extensions;

-- Replace <PROJECT_REF> and <ANON_KEY> after deploying the function, then
-- run the cron.schedule below once (in the SQL editor). It is commented out
-- so this migration is idempotent and safe to apply before deploy.
-- select cron.schedule(
--   'beruang-ai-engine',
--   '*/5 * * * *',
--   $$ select net.http_post(
--        url := 'https://<PROJECT_REF>.functions.supabase.co/ai-engine',
--        headers := jsonb_build_object('Content-Type','application/json',
--                                       'Authorization','Bearer <ANON_KEY>'),
--        body := jsonb_build_object('trigger','cron')
--      ) $$
-- );
