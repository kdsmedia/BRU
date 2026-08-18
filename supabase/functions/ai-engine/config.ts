// BERUANG AI engine — configuration from environment variables.
// All knobs live here so the rest of the engine stays pure logic.
// See AI_INTEGRATION.md for the full variable reference.

export interface AIConfig {
  // Master switch. When false the engine performs no AI actions at all
  // (the app keeps working normally for human users — README §20).
  enabled: boolean;

  // AI provider (OpenAI-compatible chat completions endpoint).
  // DEFAULT: Groq (https://api.groq.com/openai/v1) — FREE forever tier, free
  // API key from https://console.groq.com/keys. Uses open-source models
  // (llama-3.3-70b-versatile) at no cost, OpenAI-compatible wire format, so
  // the engine works without any paid OpenAI billing.
  // To use paid OpenAI instead, set AI_API_BASE=https://api.openai.com/v1
  // and AI_MODEL=gpt-4o-mini (or similar) with your OpenAI key.
  apiBase: string;      // e.g. https://api.groq.com/openai/v1 (free) or https://api.openai.com/v1
  apiKey: string;       // secret — MUST come from env, never the APK/frontend
  model: string;        // e.g. llama-3.3-70b-versatile (Groq free) or gpt-4o-mini (OpenAI paid)
  requestTimeoutMs: number;

  // Activity probabilities (0-100). Chosen per eligible AI per tick.
  postProbability: number;
  commentProbability: number;
  replyProbability: number;
  likeProbability: number;
  followProbability: number;

  // Daily / hourly caps per AI (README §11).
  maxPostsPerDay: number;
  maxCommentsPerHour: number;
  maxRepliesPerHour: number;
  maxLikesPerHour: number;
  maxFollowsPerDay: number;

  // Anti-spam (README §13).
  cooldownSeconds: number;          // min gap between any two actions by one AI
  duplicateSimilarity: number;      // 0-1, reject content above this Jaccard sim to recent
  targetCooldownSeconds: number;    // min gap before acting on the same target again

  // How many AI agents to consider acting per tick (keeps activity natural).
  maxAgentsPerTick: number;

  // Feed sampling size when choosing a post to engage with.
  feedSampleSize: number;
}

function num(v: string | undefined, fallback: number): number {
  if (v === undefined || v === "") return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function bool(v: string | undefined, fallback: boolean): boolean {
  if (v === undefined || v === "") return fallback;
  return v === "true" || v === "1" || v === "yes";
}

export function loadConfig(): AIConfig {
  return {
    enabled:            bool(Deno.env.get("AI_ENABLED"), true),
    apiBase:            Deno.env.get("AI_API_BASE") || "https://api.groq.com/openai/v1",
    apiKey:             Deno.env.get("AI_API_KEY") || "",
    model:              Deno.env.get("AI_MODEL") || "llama-3.3-70b-versatile",
    requestTimeoutMs:   num(Deno.env.get("AI_REQUEST_TIMEOUT_MS"), 20000),

    postProbability:    num(Deno.env.get("AI_POST_PROBABILITY"), 20),
    commentProbability: num(Deno.env.get("AI_COMMENT_PROBABILITY"), 35),
    replyProbability:   num(Deno.env.get("AI_REPLY_PROBABILITY"), 60),
    likeProbability:    num(Deno.env.get("AI_LIKE_PROBABILITY"), 55),
    followProbability:  num(Deno.env.get("AI_FOLLOW_PROBABILITY"), 10),

    maxPostsPerDay:     num(Deno.env.get("AI_MAX_POSTS_PER_DAY"), 5),
    maxCommentsPerHour: num(Deno.env.get("AI_MAX_COMMENTS_PER_HOUR"), 10),
    maxRepliesPerHour:  num(Deno.env.get("AI_MAX_REPLIES_PER_HOUR"), 10),
    maxLikesPerHour:    num(Deno.env.get("AI_MAX_LIKES_PER_HOUR"), 30),
    maxFollowsPerDay:   num(Deno.env.get("AI_MAX_FOLLOWS_PER_DAY"), 5),

    cooldownSeconds:        num(Deno.env.get("AI_COOLDOWN_SECONDS"), 120),
    duplicateSimilarity:    num(Deno.env.get("AI_DUPLICATE_SIMILARITY"), 0.6),
    targetCooldownSeconds:  num(Deno.env.get("AI_TARGET_COOLDOWN_SECONDS"), 600),

    maxAgentsPerTick:   num(Deno.env.get("AI_MAX_AGENTS_PER_TICK"), 3),
    feedSampleSize:     num(Deno.env.get("AI_FEED_SAMPLE_SIZE"), 30),
  };
}

export type ActivityType = "POST" | "COMMENT" | "REPLY" | "LIKE" | "FOLLOW";
