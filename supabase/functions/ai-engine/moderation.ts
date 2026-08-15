// BERUANG AI engine — content moderation & duplicate/similarity detection.
// Every generated text passes Generate -> Validate -> Moderate -> Publish
// (README §18). Nothing raw from the LLM is written without validation.

const BANNED = [
  // violence / harm
  "bunuh", "bakar hidup", "bom", "menyiksa", "pedang", "senjata api",
  // hate / harassment
  "babi", "monyet", "anjing lu", "bangsat", "kontol", "memek", "pepek",
  "ngentot", "jewel", "kafir", "cina anj",
  // sexual / explicit
  "porn", "bokep", "telanjang", "ngesex", "seksi", "bugil",
  // drugs / illegal
  "sabu", "ganja", "ekstasi", "narkoba", "jual obat",
  // self-harm
  "bunuh diri", "melukai diri",
];

const BANNED_PATTERNS = [
  /\bhttps?:\/\/\S+/gi,        // no raw URLs (anti phishing/spam)
  /\b\d{10,}\b/g,              // no phone-number dumps
  /\b[\w.+-]+@[\w-]+\.\w+\b/gi, // no email dumps
];

export interface ModerationResult {
  ok: boolean;
  reason?: string;
  cleaned: string;
}

/** Quick similarity via token Jaccard (good enough for short social text). */
export function similarity(a: string, b: string): number {
  const ta = new Set(tokenize(a));
  const tb = new Set(tokenize(b));
  if (ta.size === 0 || tb.size === 0) return 0;
  let inter = 0;
  for (const t of ta) if (tb.has(t)) inter++;
  return inter / (ta.size + tb.size - inter);
}

function tokenize(s: string): string[] {
  return s.toLowerCase().split(/[^a-z0-9]+/i).filter((t) => t.length > 2);
}

/**
 * Validate + moderate a piece of generated content.
 * Returns ok:false when the content must be rejected.
 */
export function moderate(text: string): ModerationResult {
  let cleaned = String(text ?? "").trim();

  // Reject empty / too short / too long
  if (!cleaned) return { ok: false, reason: "empty", cleaned };
  if (cleaned.length < 3) return { ok: false, reason: "too_short", cleaned };
  if (cleaned.length > 500) cleaned = cleaned.slice(0, 500).trim();

  const lower = cleaned.toLowerCase();

  // Banned words
  for (const w of BANNED) {
    if (lower.includes(w)) {
      return { ok: false, reason: `banned_word:${w}`, cleaned };
    }
  }

  // Strip/replace banned patterns
  cleaned = cleaned.replace(/\bhttps?:\/\/\S+/gi, "(link)");
  cleaned = cleaned.replace(/\b\d{10,}\b/g, "");
  cleaned = cleaned.replace(/\b[\w.+-]+@[\w-]+\.\w+\b/gi, "");
  cleaned = cleaned.replace(/\s+/g, " ").trim();

  if (!cleaned || cleaned.length < 3) {
    return { ok: false, reason: "only_blocked_patterns", cleaned };
  }
  // Suppress BANNED_PATTERNS lint by referencing it (patterns applied above)
  void BANNED_PATTERNS;

  return { ok: true, cleaned };
}

/** Reject if the content is too similar to any recent entry. */
export function isDuplicate(
  candidate: string,
  recent: string[],
  threshold: number
): boolean {
  for (const r of recent) {
    if (similarity(candidate, r) >= threshold) return true;
  }
  return false;
}
