// BERUANG AI engine — per-AI memory (README §9).
// Stores only what is needed: topics, conversation summaries, interacted
// users, and short context. Each AI has isolated memory rows.

import { sb } from "./db.ts";

export interface MemoryRow {
  kind: "topic" | "summary" | "interacted_user" | "context";
  key: string;
  content: Record<string, unknown>;
}

/** Load all memory rows for an AI agent. */
export async function loadMemory(aiUserId: string): Promise<MemoryRow[]> {
  const { data, error } = await sb
    .from("ai_memory")
    .select("kind,key,content")
    .eq("ai_user_id", aiUserId);
  if (error) throw error;
  return (data ?? []).map((r) => ({
    kind: r.kind as MemoryRow["kind"],
    key: r.key,
    content: r.content as Record<string, unknown>,
  }));
}

/** Upsert a memory row. */
export async function saveMemory(
  aiUserId: string,
  kind: MemoryRow["kind"],
  key: string,
  content: Record<string, unknown>
): Promise<void> {
  const { error } = await sb
    .from("ai_memory")
    .upsert(
      { ai_user_id: aiUserId, kind, key, content, updated_at: new Date().toISOString() },
      { onConflict: "ai_user_id,kind,key" }
    );
  if (error) throw error;
}

/** Record that this AI interacted with a user (for follow/reply diversity). */
export async function rememberInteraction(
  aiUserId: string,
  targetUid: string,
  context: string
): Promise<void> {
  await saveMemory(aiUserId, "interacted_user", targetUid, {
    lastAt: Date.now(),
    context,
  });
}

/** Build a compact memory digest for the LLM system prompt. */
export function memoryDigest(rows: MemoryRow[]): string {
  if (rows.length === 0) return "(belum ada ingatan)";
  const parts: string[] = [];
  const topics = rows.filter((r) => r.kind === "topic").map((r) => r.key);
  if (topics.length) parts.push(`Topik yang pernah dibahas: ${topics.slice(0, 8).join(", ")}`);
  const users = rows.filter((r) => r.kind === "interacted_user");
  if (users.length) parts.push(`Pernah interaksi dengan ${users.length} pengguna`);
  const summaries = rows.filter((r) => r.kind === "summary").map((r) => String(r.content.text || ""));
  if (summaries.length) parts.push(`Ringkasan: ${summaries.slice(-3).join(" | ")}`);
  return parts.join(". ") || "(belum ada ingatan)";
}
