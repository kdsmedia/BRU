// BERUANG AI engine — Edge Function entry point.
//
// Routes (all require the Authorization header = anon or service key,
// except /health):
//   POST  { trigger: "cron" }                       -> run one scheduler tick
//   POST  { action: "create_agent", persona: "andi" } -> create an AI user
//   GET   ?action=list_personas                     -> list available personas
//   GET   ?action=health                             -> health/status check
//
// The AI API key and Supabase service_role key live ONLY in this function's
// environment (README §17) — never in the APK/frontend.

import { loadConfig } from "./config.ts";
import { tick } from "./scheduler.ts";
import { AIService } from "./ai_service.ts";
import { PERSONAS, getPersona } from "./personas.ts";
import {
  sb, updateNode, getNode,
} from "./db.ts";
import { moderate } from "./moderation.ts";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Content-Type": "application/json",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: CORS });
}

/** Create a Supabase auth account + ai_agents row + nodes user/wallet for an AI. */
async function createAgent(personaId: string): Promise<Response> {
  const persona = getPersona(personaId);
  if (!persona) return json({ ok: false, error: "unknown persona", available: PERSONAS.map((p) => p.id) }, 400);

  // Synthetic email/password for the AI auth account (server-generated).
  const email = `ai+${persona.id}+${Math.random().toString(36).slice(2, 8)}@beruang.ai`;
  const password = crypto.randomUUID() + crypto.randomUUID();

  const { data, error } = await sb.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { display_name: persona.displayName, photo_url: persona.avatar, is_ai: true },
  });
  if (error) return json({ ok: false, error: error.message }, 500);

  const uid = data.user.id;

  // Shared user node (same shape as a human user, plus is_ai flag) — README §2, §19
  await updateNode(`users/${uid}`, {
    username: persona.displayName,
    photo: persona.avatar,
    uid,
    is_ai: true,
    persona: persona.id,
    bio: persona.bio,
  });

  // Wallet node (keeps the AI consistent with the wallet-based tier system)
  await updateNode(`wallets/${uid}`, {
    acctId: String(Math.floor(100000 + Math.random() * 900000)),
    balance: 0,
    tier: "Gold",
    role: "user",
  });

  // Backend-only agent metadata row
  const activeRange = `[${persona.activeHours[0]},${persona.activeHours[1]})`;
  const { error: insErr } = await sb.from("ai_agents").insert({
    ai_user_id: uid,
    persona: persona.id,
    display_name: persona.displayName,
    interests: persona.interests,
    active_hours: activeRange,
    is_active: true,
    counters_date: new Date().toISOString().slice(0, 10),
  });
  if (insErr) return json({ ok: false, error: insErr.message, uid }, 500);

  return json({ ok: true, uid, persona: persona.id, displayName: persona.displayName, email });
}

/** Toggle an AI agent on/off (pause without deleting). */
async function toggleAgent(uid: string, active: boolean): Promise<Response> {
  const { error } = await sb.from("ai_agents").update({ is_active: active }).eq("ai_user_id", uid);
  if (error) return json({ ok: false, error: error.message }, 500);
  return json({ ok: true, uid, is_active: active });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const cfg = loadConfig();

  // Health/status — safe to call without auth (returns config summary only)
  const url = new URL(req.url);
  if (req.method === "GET" && url.searchParams.get("action") === "health") {
    return json({
      ok: true,
      enabled: cfg.enabled,
      provider: { base: cfg.apiBase, model: cfg.model, hasKey: Boolean(cfg.apiKey) },
      probabilities: {
        post: cfg.postProbability, comment: cfg.commentProbability, reply: cfg.replyProbability,
        like: cfg.likeProbability, follow: cfg.followProbability,
      },
      limits: {
        postsPerDay: cfg.maxPostsPerDay, commentsPerHour: cfg.maxCommentsPerHour,
        repliesPerHour: cfg.maxRepliesPerHour, likesPerHour: cfg.maxLikesPerHour,
        followsPerDay: cfg.maxFollowsPerDay,
      },
      personas: PERSONAS.map((p) => p.id),
    });
  }

  if (req.method === "GET" && url.searchParams.get("action") === "list_personas") {
    return json({ personas: PERSONAS.map((p) => ({ id: p.id, displayName: p.displayName, bio: p.bio, interests: p.interests })) });
  }

  // All remaining actions are POST with a JSON body.
  let body: Record<string, unknown> = {};
  if (req.method === "POST") {
    try { body = await req.json(); } catch { return json({ ok: false, error: "invalid json" }, 400); }
  }

  // Cron trigger — the scheduler tick (called by pg_cron every N minutes).
  if (body.trigger === "cron" || body.action === "tick") {
    if (!cfg.enabled) return json({ ok: true, enabled: false, message: "AI disabled by env" });
    const ai = new AIService(cfg);
    try {
      const result = await tick(cfg, ai);
      return json({ ok: true, ...result });
    } catch (e) {
      return json({ ok: false, error: e instanceof Error ? e.message : String(e) }, 500);
    }
  }

  // Create a new AI user.
  if (body.action === "create_agent") {
    const persona = String(body.persona || "");
    return await createAgent(persona);
  }

  // Pause/resume an AI user.
  if (body.action === "toggle_agent") {
    const uid = String(body.uid || "");
    const active = Boolean(body.active);
    return await toggleAgent(uid, active);
  }

  // Preview moderation (handy when testing).
  if (body.action === "moderate") {
    const text = String(body.text || "");
    const m = moderate(text);
    return json(m);
  }

  return json({ ok: false, error: "unknown action", actions: ["cron/tick", "create_agent", "toggle_agent", "moderate"], gets: ["health", "list_personas"] }, 400);
});
