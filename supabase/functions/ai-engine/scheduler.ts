// BERUANG AI engine — scheduler / orchestrator (README §10, §12, §13).
// Picks eligible AI agents, decides an activity, generates content,
// moderates it, then publishes to the SAME nodes paths a human user would
// use — so the existing feed/UI needs no changes to show AI content.

import type { AIConfig, ActivityType } from "./config.ts";
import {
  sb, getNode, updateNode, setNode, listChildren, pushId,
} from "./db.ts";
import { AIService } from "./ai_service.ts";
import { getPersona } from "./personas.ts";
import { rememberInteraction } from "./memory.ts";
import { moderate } from "./moderation.ts";

interface AgentRow {
  ai_user_id: string;
  persona: string;
  display_name: string;
  interests: string[];
  active_hours: string;   // int4range text like "[8,23)"
  is_active: boolean;
  last_activity_at: string | null;
  posts_today: number;
  comments_today: number;
  replies_today: number;
  likes_today: number;
  follows_today: number;
  counters_date: string | null;
}

interface Post {
  pid: string;
  uid: string;
  caption?: string;
  image?: string;
  timestamp: number;
  comments?: Record<string, { text: string; uid: string; username: string; timestamp: number }>;
}

const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;

/** Reset daily counters if the day rolled over. */
function maybeResetCounters(a: AgentRow): AgentRow {
  const today = new Date().toISOString().slice(0, 10);
  if (a.counters_date !== today) {
    a.posts_today = a.comments_today = a.replies_today = a.likes_today = a.follows_today = 0;
    a.counters_date = today;
  }
  return a;
}

/** Is the agent inside its active-hour window right now? */
function isWithinActiveHours(a: AgentRow): boolean {
  const m = /^(\[|\()(\d+),(\d+)(\)|\])$/.exec(a.active_hours || "");
  if (!m) return true;
  const start = parseInt(m[2], 10);
  const end = parseInt(m[3], 10);
  const now = new Date();
  // treat hours in 0-24; allow wrap (end<=start => always active)
  const h = now.getHours();
  if (end <= start) return true;
  return h >= start && h < end;
}

function rand(): number {
  return Math.random();
}

function pick<T>(arr: T[]): T | undefined {
  return arr.length ? arr[Math.floor(Math.random() * arr.length)] : undefined;
}

/** Load active AI agents, reset stale counters, and return those eligible to act now. */
async function eligibleAgents(cfg: AIConfig): Promise<AgentRow[]> {
  const { data, error } = await sb
    .from("ai_agents")
    .select("*")
    .eq("is_active", true)
    .limit(cfg.maxAgentsPerTick * 4);
  if (error) throw error;

  const now = Date.now();
  const out: AgentRow[] = [];
  for (const a of (data ?? []) as AgentRow[]) {
    maybeResetCounters(a);
    if (!isWithinActiveHours(a)) continue;
    // global cooldown between this agent's actions
    if (a.last_activity_at && now - new Date(a.last_activity_at).getTime() < cfg.cooldownSeconds * 1000) {
      continue;
    }
    out.push(a);
  }
  // shuffle for natural, unpredictable ordering (README §12)
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out.slice(0, cfg.maxAgentsPerTick);
}

/** Decide which activity an agent should attempt, respecting caps & probability. */
function chooseActivity(a: AgentRow, cfg: AIConfig): ActivityType | null {
  const roll = rand() * 100;
  // Priority: reply > comment > like > follow > post (keeps threads alive)
  if (a.replies_today < cfg.maxRepliesPerHour && roll < cfg.replyProbability) return "REPLY";
  if (a.comments_today < cfg.maxCommentsPerHour && roll < cfg.commentProbability) return "COMMENT";
  if (a.likes_today < cfg.maxLikesPerHour && roll < cfg.likeProbability) return "LIKE";
  if (a.follows_today < cfg.maxFollowsPerDay && roll < cfg.followProbability) return "FOLLOW";
  if (a.posts_today < cfg.maxPostsPerDay && roll < cfg.postProbability) return "POST";
  return null;
}

/** Fetch the latest N posts (mirror of the client's listenFeed query). */
async function recentPosts(cfg: AIConfig): Promise<Post[]> {
  const children = await listChildren<Post>("posts");
  // newest first
  children.sort((a, b) => (b[1]?.timestamp ?? 0) - (a[1]?.timestamp ?? 0));
  return children.slice(0, cfg.feedSampleSize).map(([pid, post]) => ({ pid, ...(post as object) } as Post));
}

/** Read a few recent captions by this agent (anti-duplicate context). */
async function recentAgentCaptions(aiUid: string): Promise<string[]> {
  const posts = await recentPosts({ feedSampleSize: 60 } as AIConfig);
  return posts
    .filter((p) => p.uid === aiUid)
    .map((p) => p.caption || "")
    .filter(Boolean)
    .slice(0, 10);
}

/** Trending topics = most common caption tokens across recent posts. */
function trendingTopics(posts: Post[]): string[] {
  const freq = new Map<string, number>();
  for (const p of posts) {
    for (const tok of (p.caption || "").toLowerCase().split(/[^a-z0-9]+/i)) {
      if (tok.length > 4) freq.set(tok, (freq.get(tok) || 0) + 1);
    }
  }
  return [...freq.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(([t]) => t);
}

/** Persist an activity log row (internal debugging only — README §15). */
async function logActivity(
  a: AgentRow, type: ActivityType, status: "ok" | "skipped" | "error",
  extra: { targetPostId?: string; targetUserId?: string; content?: string; error?: string } = {}
): Promise<void> {
  try {
    await sb.from("ai_activity_logs").insert({
      ai_user_id: a.ai_user_id,
      activity_type: type,
      target_post_id: extra.targetPostId ?? null,
      target_user_id: extra.targetUserId ?? null,
      generated_content: extra.content ?? null,
      status,
      error: extra.error ?? null,
    });
  } catch {
    // logging must never break the engine
  }
}

/** Mark the agent as having acted (cooldown + counters). */
async function bumpAgent(a: AgentRow, type: ActivityType): Promise<void> {
  const patch: Record<string, unknown> = { last_activity_at: new Date().toISOString() };
  const map: Record<ActivityType, string> = {
    POST: "posts_today", COMMENT: "comments_today", REPLY: "replies_today",
    LIKE: "likes_today", FOLLOW: "follows_today",
  };
  patch[map[type]] = (a as unknown as Record<string, number>)[map[type]] + 1;
  await sb.from("ai_agents").update(patch).eq("ai_user_id", a.ai_user_id);
  (a as unknown as Record<string, number>)[map[type]] += 1;
  a.last_activity_at = new Date().toISOString();
}

// ---------- publishers (write to the same nodes paths as the client) ----------

async function publishPost(aiUid: string, caption: string): Promise<string> {
  const pid = pushId();
  await setNode(`posts/${pid}`, {
    uid: aiUid, caption, timestamp: Date.now(),
  });
  return pid;
}

async function publishComment(pid: string, aiUid: string, name: string, text: string): Promise<void> {
  const cid = pushId();
  await setNode(`posts/${pid}/comments/${cid}`, {
    text, uid: aiUid, username: name, timestamp: Date.now(),
  });
}

async function publishLike(pid: string, aiUid: string): Promise<void> {
  await setNode(`posts/${pid}/likes/${aiUid}`, true);
}

async function publishFollow(aiUid: string, targetUid: string): Promise<void> {
  await setNode(`following/${aiUid}/${targetUid}`, true);
  await setNode(`followers/${targetUid}/${aiUid}`, true);
}

async function sendNotif(targetUid: string, text: string): Promise<void> {
  const id = pushId();
  await setNode(`notifications/${targetUid}/${id}`, { text, timestamp: Date.now() });
}

/** Get the AI user's display name from nodes (fallback to agent row). */
async function agentName(a: AgentRow): Promise<string> {
  const u = await getNode<{ username?: string }>(`users/${a.ai_user_id}`);
  return u?.username || a.display_name;
}

/** Run a single activity for one agent. */
async function runActivity(a: AgentRow, type: ActivityType, cfg: AIConfig, ai: AIService): Promise<void> {
  const persona = getPersona(a.persona);
  if (!persona) {
    await logActivity(a, type, "error", { error: "unknown persona" });
    return;
  }

  if (type === "POST") {
    const posts = await recentPosts(cfg);
    const gen = await ai.generatePost({
      persona,
      recentCaptions: await recentAgentCaptions(a.ai_user_id),
      trendingTopics: trendingTopics(posts),
    });
    if (!gen) {
      await logActivity(a, "POST", "skipped", { error: "generation rejected/empty" });
      return;
    }
    const pid = await publishPost(a.ai_user_id, gen.text);
    await bumpAgent(a, "POST");
    await logActivity(a, "POST", "ok", { targetPostId: pid, content: gen.text });
    return;
  }

  // Engagement activities need a target post
  const posts = await recentPosts(cfg);
  if (posts.length === 0) {
    await logActivity(a, type, "skipped", { error: "no posts to engage" });
    return;
  }

  if (type === "LIKE") {
    // Like with probability gated by relevance — don't like everything (README §7)
    const candidates = posts.filter((p) => p.uid !== a.ai_user_id);
    const target = pick(candidates);
    if (!target) { await logActivity(a, "LIKE", "skipped", { error: "no target" }); return; }
    const score = await ai.analyzePost(persona, target.caption || "");
    if (rand() * 100 > Math.min(95, score)) {
      await logActivity(a, "LIKE", "skipped", { targetPostId: target.pid, error: "low relevance" });
      return;
    }
    await publishLike(target.pid, a.ai_user_id);
    if (target.uid !== a.ai_user_id) await sendNotif(target.uid, "liked your post");
    await bumpAgent(a, "LIKE");
    await logActivity(a, "LIKE", "ok", { targetPostId: target.pid, targetUserId: target.uid });
    return;
  }

  if (type === "FOLLOW") {
    const candidates = posts.filter((p) => p.uid !== a.ai_user_id);
    const target = pick(candidates);
    if (!target) { await logActivity(a, "FOLLOW", "skipped", { error: "no target" }); return; }
    // don't re-follow
    const already = await getNode(`following/${a.ai_user_id}/${target.uid}`);
    if (already) { await logActivity(a, "FOLLOW", "skipped", { error: "already following" }); return; }
    await publishFollow(a.ai_user_id, target.uid);
    await sendNotif(target.uid, "started following you");
    await rememberInteraction(a.ai_user_id, target.uid, "follow");
    await bumpAgent(a, "FOLLOW");
    await logActivity(a, "FOLLOW", "ok", { targetUserId: target.uid });
    return;
  }

  // COMMENT / REPLY
  const candidates = posts.filter((p) => p.uid !== a.ai_user_id);
  const target = pick(candidates);
  if (!target) { await logActivity(a, type, "skipped", { error: "no target" }); return; }

  const recentComments = target.comments
    ? Object.values(target.comments).slice(-8).map((c) => `${c.username}: ${c.text}`)
    : [];

  let gen;
  if (type === "REPLY") {
    // A "reply" = comment that continues the existing thread context.
    gen = await ai.generateReply({
      persona,
      postCaption: target.caption || "",
      postAuthorName: (await agentNameFor(target.uid)) || "pengguna",
      recentComments,
      threadContext: recentComments.join("\n"),
    });
  } else {
    gen = await ai.generateComment({
      persona,
      postCaption: target.caption || "",
      postAuthorName: (await agentNameFor(target.uid)) || "pengguna",
      recentComments,
      threadContext: "",
    });
  }
  if (!gen) {
    await logActivity(a, type, "skipped", { targetPostId: target.pid, error: "generation rejected" });
    return;
  }
  const name = await agentName(a);
  await publishComment(target.pid, a.ai_user_id, name, gen.text);
  if (target.uid !== a.ai_user_id) await sendNotif(target.uid, `commented: ${gen.text}`);
  await rememberInteraction(a.ai_user_id, target.uid, gen.text);
  await bumpAgent(a, type);
  await logActivity(a, type, "ok", { targetPostId: target.pid, targetUserId: target.uid, content: gen.text });
}

async function agentNameFor(uid: string): Promise<string | null> {
  const u = await getNode<{ username?: string }>(`users/${uid}`);
  return u?.username || null;
}

/** One scheduler tick: consider a few agents, let each do at most one activity. */
export async function tick(cfg: AIConfig, ai: AIService): Promise<{ considered: number; acted: number }> {
  if (!cfg.enabled) return { considered: 0, acted: 0 };

  const agents = await eligibleAgents(cfg);
  let acted = 0;
  for (const a of agents) {
    const type = chooseActivity(a, cfg);
    if (!type) continue;
    try {
      await runActivity(a, type, cfg, ai);
      acted++;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      await logActivity(a, type, "error", { error: msg });
    }
  }
  return { considered: agents.length, acted };
}

// Re-export helpers used by the create-AI-user endpoint.
export { moderate };
