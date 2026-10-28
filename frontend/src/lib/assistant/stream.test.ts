import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import { type AssistantEvent, askAssistant, parseSseChunk } from "./stream";

/** 本文を、指定した区切りで少しずつ返す Response（SSE が細切れに届く状態を作る） */
function streamResponse(chunks: (string | Uint8Array)[], init: ResponseInit = {}): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(typeof chunk === "string" ? encoder.encode(chunk) : chunk);
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { "content-type": "text/event-stream" }, ...init });
}

describe("parseSseChunk", () => {
  it("区切り（空行）まで届いたイベントだけを返し、続きを残す", () => {
    const { events, rest } = parseSseChunk('event:unanswerable\ndata:{"reason":"NO_EVIDENCE"}\n\nevent:done\nda');
    expect(events).toEqual([{ type: "unanswerable", data: { reason: "NO_EVIDENCE" } }]);
    expect(rest).toBe("event:done\nda");
  });

  it("複数行の data と CRLF を読み、知らないイベントは無視する", () => {
    const { events } = parseSseChunk('event:unanswerable\r\ndata:{"reason":\r\ndata: "X"}\r\n\r\nevent:ping\r\ndata:{}\r\n\r\n');
    expect(events).toEqual([{ type: "unanswerable", data: { reason: "X" } }]);
  });
});

describe("askAssistant", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("CSRF トークンを付けて POST し、細切れに届いたイベントを順に渡す", async () => {
    // 文字の途中（UTF-8 のバイトの途中）でも区切って届ける
    const answer = new TextEncoder().encode('event:answer\ndata:{"answer":"30,000円です。[1]","evidence":[],"model":"m"}\n\n');
    const cut = 40;
    fetchMock.mockResolvedValueOnce(
      streamResponse([
        'event:results\ndata:{"fallback":false}\n',
        "\n",
        answer.slice(0, cut),
        answer.slice(cut),
        "event:done\ndata:{}",
      ]),
    );
    const events: AssistantEvent[] = [];

    await askAssistant("結婚祝金はいくら？", (e) => events.push(e));

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("/api/v1/assistant/answers");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("t");
    expect(JSON.parse(init?.body as string)).toEqual({ question: "結婚祝金はいくら？" });
    expect(events.map((e) => e.type)).toEqual(["results", "answer", "done"]);
    expect(events[1]).toMatchObject({ data: { answer: "30,000円です。[1]" } });
  });

  it("401 ならログイン画面へ移動する処理を呼ぶ", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    const onUnauthorized = vi.fn();

    await expect(askAssistant("q", () => {}, undefined, onUnauthorized)).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("400 は Problem Details の detail を持つ ApiError にする", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ detail: "知りたいことを入力してください。" }, { status: 400 }));

    await expect(askAssistant(" ", () => {})).rejects.toMatchObject({ status: 400, detail: "知りたいことを入力してください。" });
  });
});
