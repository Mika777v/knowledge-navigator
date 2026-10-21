"use client";

import { useState } from "react";
import { sendFeedback } from "@/lib/search/log";

const MAX_COMMENT_LENGTH = 1000;

/** 「この結果は役に立ちましたか？」。役に立たなかったときは、探していたものを書いてもらう（任意） */
export function Feedback({ searchLogId }: { searchLogId: string }) {
  const [state, setState] = useState<"ask" | "comment" | "sending" | "done" | "error">("ask");
  const [comment, setComment] = useState("");

  async function send(helpful: boolean, text?: string) {
    setState("sending");
    try {
      await sendFeedback(searchLogId, helpful, text);
      setState("done");
    } catch {
      setState("error");
    }
  }

  if (state === "done") {
    return <p className="text-sm text-muted-foreground">ご協力ありがとうございました。検索の改善に使います。</p>;
  }
  return (
    <section aria-labelledby="feedback-heading" className="flex flex-col gap-2 rounded-lg border border-border p-4 text-sm">
      <h2 id="feedback-heading" className="font-semibold">
        この結果は役に立ちましたか？
      </h2>
      {state === "comment" ? (
        <form
          className="flex flex-col gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void send(false, comment);
          }}
        >
          <label className="flex flex-col gap-1">
            探していたもの（任意）
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              maxLength={MAX_COMMENT_LENGTH}
              rows={3}
              className="rounded-md border border-border bg-background px-2 py-1"
            />
          </label>
          <p className="text-xs text-muted-foreground">個人を特定できる情報（氏名・社員番号など）は書かないでください。</p>
          <button type="submit" className="self-start rounded-md border border-border px-3 py-1">
            送信
          </button>
        </form>
      ) : (
        <div className="flex gap-2">
          <button
            type="button"
            disabled={state === "sending"}
            onClick={() => void send(true)}
            className="rounded-md border border-border px-3 py-1"
          >
            はい
          </button>
          <button
            type="button"
            disabled={state === "sending"}
            onClick={() => setState("comment")}
            className="rounded-md border border-border px-3 py-1"
          >
            いいえ
          </button>
        </div>
      )}
      {state === "error" && (
        <p role="alert" className="text-red-600 dark:text-red-400">
          送信できませんでした。時間をおいて再度お試しください。
        </p>
      )}
    </section>
  );
}
