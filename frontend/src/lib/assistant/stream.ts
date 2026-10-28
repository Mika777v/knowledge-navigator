import { ApiError, ensureXsrfToken, redirectToLogin } from "@/lib/api/http";
import type { AssistantAnswer, AssistantUnanswerable, NavigationResult } from "@/lib/api/types";

/** 根拠付き回答の SSE のイベント（backend の AssistantController） */
export type AssistantEvent =
  | { type: "results"; data: NavigationResult }
  | { type: "answer"; data: AssistantAnswer }
  | { type: "unanswerable"; data: AssistantUnanswerable }
  | { type: "done" };

/**
 * SSE の本文を少しずつ読み、イベントの区切り（空行）までたまったものから返す。
 * 返り値の rest は、まだ区切りが来ていない続き（次に読んだ分の前に付ける）。
 */
export function parseSseChunk(buffer: string): { events: AssistantEvent[]; rest: string } {
  const blocks = buffer.split(/\r?\n\r?\n/);
  const rest = blocks.pop() ?? "";
  const events: AssistantEvent[] = [];
  for (const block of blocks) {
    let name = "message";
    const data: string[] = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith("event:")) name = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
    }
    if (name === "done") events.push({ type: "done" });
    else if ((name === "results" || name === "answer" || name === "unanswerable") && data.length > 0) {
      events.push({ type: name, data: JSON.parse(data.join("\n")) } as AssistantEvent);
    }
  }
  return { events, rest };
}

/**
 * 根拠付き回答を求め、届いたイベントを順に onEvent に渡す。done まで読み終えたら返る。
 * 401 は onUnauthorized（既定はログイン画面へ）、それ以外の失敗は ApiError を投げる。
 */
export async function askAssistant(
  question: string,
  onEvent: (event: AssistantEvent) => void,
  signal?: AbortSignal,
  onUnauthorized: () => void = redirectToLogin,
): Promise<void> {
  const response = await fetch("/api/v1/assistant/answers", {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": await ensureXsrfToken(),
    },
    body: JSON.stringify({ question }),
    credentials: "same-origin",
    signal,
  });
  if (response.status === 401) {
    onUnauthorized();
    throw new ApiError(401, "ログインが必要です。");
  }
  if (!response.ok || !response.body) {
    let detail = "回答を取得できませんでした。時間をおいて再度お試しください。";
    try {
      detail = ((await response.json()) as { detail?: string }).detail ?? detail;
    } catch {
      // 本文が JSON でなければ既定のメッセージにする
    }
    throw new ApiError(response.status, detail);
  }

  const reader = response.body.getReader();
  // 文字の途中で区切られて届いても、次の分とつないで読む
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    const parsed = parseSseChunk(buffer + decoder.decode(value, { stream: true }));
    buffer = parsed.rest;
    for (const event of parsed.events) onEvent(event);
  }
  for (const event of parseSseChunk(buffer + decoder.decode() + "\n\n").events) onEvent(event);
}
