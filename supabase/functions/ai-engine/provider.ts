// BERUANG AI engine — LLM provider (OpenAI-compatible chat completions).
// The AIService talks only through this module, so the provider can be
// swapped (OpenAI, Groq, OpenRouter, local Ollama, ...) without touching
// the rest of the app (README §16).

import type { AIConfig } from "./config.ts";

export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

export interface ChatResult {
  text: string;
  ok: boolean;
  error?: string;
}

/**
 * Call the configured chat-completions endpoint. Returns ok:false (never
 * throws) on any failure so callers can apply safe error handling per
 * README §20: no empty posts, log error, cooldown, app keeps running.
 */
export async function chat(
  cfg: AIConfig,
  messages: ChatMessage[],
  opts: { maxTokens?: number; temperature?: number } = {}
): Promise<ChatResult> {
  if (!cfg.apiKey) {
    return { ok: false, text: "", error: "AI_API_KEY not configured" };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), cfg.requestTimeoutMs);

  try {
    const res = await fetch(`${cfg.apiBase}/chat/completions`, {
      method: "POST",
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${cfg.apiKey}`,
      },
      body: JSON.stringify({
        model: cfg.model,
        messages,
        max_tokens: opts.maxTokens ?? 280,
        temperature: opts.temperature ?? 0.8,
      }),
    });

    if (!res.ok) {
      const body = await res.text().catch(() => "");
      return { ok: false, text: "", error: `HTTP ${res.status}: ${body.slice(0, 160)}` };
    }

    const json = await res.json();
    const text = json?.choices?.[0]?.message?.content?.trim() ?? "";
    if (!text) return { ok: false, text: "", error: "empty completion" };
    return { ok: true, text };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return { ok: false, text: "", error: msg };
  } finally {
    clearTimeout(timer);
  }
}
