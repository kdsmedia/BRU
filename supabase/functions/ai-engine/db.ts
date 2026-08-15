// BERUANG AI engine — database access.
// Uses the Supabase service_role client (server-only) to:
//   - read/write the app's shared `nodes` tree (same paths the client uses),
//   - manage AI auth accounts (auth.admin),
//   - read/write the AI metadata tables (ai_agents / ai_memory / ai_activity_logs).
//
// The service_role key bypasses RLS and MUST never ship in the APK/frontend.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
  // Fail fast with a clear message — never silently fall back to anon.
  throw new Error(
    "ai-engine: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set"
  );
}

export const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});

// ---- nodes tree (the app's shared key/value "database") -----------------

/** Read one node value by path. */
export async function getNode<T = unknown>(path: string): Promise<T | null> {
  const { data, error } = await sb
    .from("nodes")
    .select("value")
    .eq("path", path)
    .maybeSingle();
  if (error) throw error;
  return (data?.value as T) ?? null;
}

/**
 * Merge-update a node (Firebase-style update): writes each leaf of `patch`
 * as its own row under `path`, exactly like the client adapter's update().
 */
export async function updateNode(path: string, patch: Record<string, unknown>): Promise<void> {
  const rows: { path: string; value: unknown }[] = [];
  const walk = (prefix: string, obj: Record<string, unknown>) => {
    for (const [k, v] of Object.entries(obj)) {
      const childPath = `${prefix}/${k}`;
      if (v && typeof v === "object" && !Array.isArray(v)) {
        walk(childPath, v as Record<string, unknown>);
      } else {
        rows.push({ path: childPath, value: v });
      }
    }
  };
  walk(path, patch);
  const { error } = await sb.from("nodes").upsert(rows, { onConflict: "path" });
  if (error) throw error;
}

/** Overwrite a single leaf node. */
export async function setNode(path: string, value: unknown): Promise<void> {
  const { error } = await sb
    .from("nodes")
    .upsert({ path, value }, { onConflict: "path" });
  if (error) throw error;
}

/** Remove a node and any descendants (path prefix delete). */
export async function removeNode(path: string): Promise<void> {
  const { error } = await sb
    .from("nodes")
    .delete()
    .or(`path.eq.${path},path.like.${path}/%`);
  if (error) throw error;
}

/**
 * List the direct children of a path as [key, value] entries.
 * Mirrors Firebase `onValue(ref(parentPath))` shallow read.
 */
export async function listChildren<T = unknown>(
  parentPath: string
): Promise<[string, T][]> {
  // match direct children only: parentPath/key  (no further slash)
  const { data, error } = await sb
    .from("nodes")
    .select("path,value")
    .like("path", `${parentPath}/%`);
  if (error) throw error;
  const out: [string, T][] = [];
  for (const row of data ?? []) {
    const rel = String(row.path).slice(parentPath.length + 1);
    if (rel.includes("/")) continue; // not a direct child
    out.push([rel, row.value as T]);
  }
  return out;
}

/** Generate a Firebase-like sortable push id (time-ordered). */
export function pushId(): string {
  const t = Date.now();
  const rand = Math.random().toString(36).slice(2, 8);
  return `${t.toString(36)}${rand}`;
}
