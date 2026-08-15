// BERUANG AI engine — the single AIService abstraction layer (README §16).
// All generation goes through here; swapping the provider only requires
// changing provider.ts, not the rest of the app.

import type { AIConfig } from "./config.ts";
import { chat, type ChatMessage } from "./provider.ts";
import { moderate, isDuplicate } from "./moderation.ts";
import { loadMemory, memoryDigest } from "./memory.ts";
import type { Persona } from "./personas.ts";

export interface PostInput {
  persona: Persona;
  recentCaptions: string[];   // last N captions to avoid repetition
  trendingTopics: string[];
}

export interface CommentInput {
  persona: Persona;
  postCaption: string;
  postAuthorName: string;
  recentComments: string[];   // recent comments on this post
  threadContext: string;      // existing conversation (for replies)
}

export interface Generated {
  text: string;
}

function systemPrompt(persona: Persona, digest: string): string {
  return [
    `Anda adalah ${persona.displayName}, seorang pengguna media sosial Indonesia di aplikasi "BERUANG".`,
    `Gaya: ${persona.tonePrompt}`,
    `Minat: ${persona.interests.join(", ")}.`,
    `Ingatan Anda: ${digest}`,
    "Aturan ketat:",
    "- Tulis dalam Bahasa Indonesia natural, seperti pengguna sungguhan.",
    "- Maksimal ~280 karakter. Tanpa hashtag berlebihan, tanpa mention @.",
    "- Jangan mengaku sebagai AI/bot. Jangan sebut 'sebagai model bahasa'.",
    "- Jangan URL, nomor telepon, atau email.",
    "- Hindari konten sensitif, politik, SARA, dewasa, atau kekerasan.",
    "- Variasikan gaya; jangan ulang kalimat yang sama.",
  ].join("\n");
}

function sanitizeJsonLike(text: string): string {
  // Some models wrap output in quotes; strip outer quotes/whitespace.
  let t = text.trim();
  if (
    (t.startsWith('"') && t.endsWith('"')) ||
    (t.startsWith("'") && t.endsWith("'"))
  ) {
    try {
      t = JSON.parse(t);
    } catch {
      t = t.slice(1, -1);
    }
  }
  return String(t).trim();
}

export class AIService {
  constructor(private cfg: AIConfig) {}

  /** Generate a post caption for the persona (README §4). */
  async generatePost(input: PostInput): Promise<Generated | null> {
    const digest = memoryDigest(await loadMemory(input.persona.id + "_seed" /* placeholder */));
    void digest;
    const mem = await loadMemory(input.persona.id);
    const messages: ChatMessage[] = [
      { role: "system", content: systemPrompt(input.persona, memoryDigest(mem)) },
      {
        role: "user",
        content:
          `Tulis satu postingan singkat untuk beranda. Variasikan dari yang sudah ada.\n` +
          `Topik yang lagi ramai: ${input.trendingTopics.join(", ") || "bebas sesuai minatmu"}.\n` +
          `Beberapa postingan terakhirmu (jangan mirip): ${input.recentCaptions.slice(-6).join(" | ") || "(belum ada)"}.\n` +
          `Balas HANYA dengan isi caption, tanpa label atau penjelasan.`,
      },
    ];
    const res = await chat(this.cfg, messages, { maxTokens: 180, temperature: 0.9 });
    if (!res.ok) return null;
    const text = sanitizeJsonLike(res.text);
    const m = moderate(text);
    if (!m.ok || isDuplicate(m.cleaned, input.recentCaptions, this.cfg.duplicateSimilarity)) {
      return null;
    }
    return { text: m.cleaned };
  }

  /** Generate a comment on a post (README §5). */
  async generateComment(input: CommentInput): Promise<Generated | null> {
    const mem = await loadMemory(input.persona.id);
    const messages: ChatMessage[] = [
      { role: "system", content: systemPrompt(input.persona, memoryDigest(mem)) },
      {
        role: "user",
        content:
          `Komentarilah postingan berikut dengan relevan dan natural.\n` +
          `Penulis: ${input.postAuthorName}\n` +
          `Isi postingan: "${input.postCaption}"\n` +
          `Komentar lain yang sudah ada: ${input.recentComments.slice(-6).join(" | ") || "(belum ada)"}.\n` +
          (input.threadContext ? `Konteks percakapan sebelumnya: ${input.threadContext}\n` : "") +
          `Balas HANYA dengan isi komentar, tanpa label.`,
      },
    ];
    const res = await chat(this.cfg, messages, { maxTokens: 120, temperature: 0.85 });
    if (!res.ok) return null;
    const text = sanitizeJsonLike(res.text);
    const m = moderate(text);
    if (!m.ok || isDuplicate(m.cleaned, input.recentComments, this.cfg.duplicateSimilarity)) {
      return null;
    }
    return { text: m.cleaned };
  }

  /** Generate a reply to a user's reply to an AI comment (README §6). */
  async generateReply(input: CommentInput): Promise<Generated | null> {
    const mem = await loadMemory(input.persona.id);
    const messages: ChatMessage[] = [
      { role: "system", content: systemPrompt(input.persona, memoryDigest(mem)) },
      {
        role: "user",
        content:
          `Balas pesan pengguna dalam percakapan berikut, menjaga konteks.\n` +
          `Postingan asli: "${input.postCaption}"\n` +
          `Percakapan: ${input.threadContext}\n` +
          `Komentar/user yang harus kamu balas: ${input.recentComments.slice(-1).join("")}\n` +
          `Balas HANYA dengan isi balasan, singkat dan natural.`,
      },
    ];
    const res = await chat(this.cfg, messages, { maxTokens: 100, temperature: 0.85 });
    if (!res.ok) return null;
    const text = sanitizeJsonLike(res.text);
    const m = moderate(text);
    if (!m.ok) return null;
    return { text: m.cleaned };
  }

  /** Analyze a post for relevance scoring (used by chooseActivity). */
  async analyzePost(persona: Persona, caption: string): Promise<number> {
    const mem = await loadMemory(persona.id);
    const messages: ChatMessage[] = [
      { role: "system", content: systemPrompt(persona, memoryDigest(mem)) },
      {
        role: "user",
        content:
          `Beri skor relevansi 0-100 seberapa cocok postingan ini dengan minatmu.\n` +
          `Isi: "${caption}"\n` +
          `Balas HANYA angka 0-100.`,
      },
    ];
    const res = await chat(this.cfg, messages, { maxTokens: 6, temperature: 0.2 });
    if (!res.ok) return 50;
    const n = parseInt(res.text.replace(/[^0-9]/g, ""), 10);
    return Number.isFinite(n) ? Math.max(0, Math.min(100, n)) : 50;
  }

  /** Standalone moderation hook (README §16, §18). */
  moderateContent(text: string): { ok: boolean; cleaned: string; reason?: string } {
    const m = moderate(text);
    return { ok: m.ok, cleaned: m.cleaned, reason: m.reason };
  }
}
