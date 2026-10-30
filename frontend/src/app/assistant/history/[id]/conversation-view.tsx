"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api/http";
import type { ConversationDetail, ConversationMessage } from "@/lib/api/types";
import { articleTitle } from "@/lib/content";
import { formatDateTime } from "@/lib/format";
import { sectionHref } from "../../../search/search-results";
import { AnswerText, UNANSWERABLE_MESSAGES } from "../../assistant-view";
import { conversationsKey } from "../history-list";

/**
 * 会話の履歴の1件（ADR-0014 の決定 3）。根拠は開いた時点の権限で backend が読み直したもの。
 * 見られなくなった根拠は数だけ出し、中身は出さない。
 */
export function ConversationView() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data, error } = useQuery({
    queryKey: ["assistant-conversation", id],
    queryFn: () => apiFetch<ConversationDetail>(`/api/v1/assistant/conversations/${id}`),
  });
  const remove = useMutation({
    mutationFn: () => apiFetch<void>(`/api/v1/assistant/conversations/${id}`, { method: "DELETE" }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: conversationsKey });
      router.push("/assistant/history");
    },
  });

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <Link href="/assistant/history" className="self-start text-sm underline">
        履歴の一覧へ
      </Link>
      {error ? (
        <p role="alert">{error.message}</p>
      ) : !data ? (
        <p role="status">読み込み中…</p>
      ) : (
        <>
          <h1 className="text-2xl font-bold">{data.title}</h1>
          <p className="text-sm text-muted-foreground">
            {formatDateTime(data.expiresAt)} に自動で消えます（このあと質問しなかった場合）。
          </p>
          <ol className="flex flex-col gap-3">
            {data.messages.map((m, i) => (
              <MessageItem key={i} message={m} />
            ))}
          </ol>
          <button
            type="button"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm("この会話を削除します。元に戻せません。よろしいですか？")) remove.mutate();
            }}
            className="self-start rounded-md border border-border px-3 py-1 text-sm hover:bg-foreground/5 disabled:opacity-60"
          >
            この会話を削除する
          </button>
          {remove.error && <p role="alert">{remove.error.message}</p>}
        </>
      )}
    </main>
  );
}

function MessageItem({ message }: { message: ConversationMessage }) {
  if (message.role === "USER") {
    return (
      <li className="flex flex-col gap-1">
        <span className="text-xs text-muted-foreground">質問・{formatDateTime(message.createdAt)}</span>
        <p className="font-medium">{message.content}</p>
      </li>
    );
  }
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border p-3">
      <span className="text-xs text-muted-foreground">AI の回答</span>
      {message.answerable ? (
        <p className="leading-relaxed">
          {/* 根拠の番号は、今も見られる根拠だけをリンクにする */}
          <AnswerText text={message.content} evidence={message.evidence} />
        </p>
      ) : (
        <p>{UNANSWERABLE_MESSAGES[message.unanswerableReason] ?? "根拠を確かめられる回答を作れませんでした。"}</p>
      )}
      {message.evidence.length > 0 && (
        <ol className="flex flex-col gap-1 text-sm">
          {message.evidence.map((e) => (
            <li key={e.ref} id={`evidence-${e.ref}`}>
              [{e.ref}]{" "}
              <Link href={sectionHref(e.documentId, e.sectionId, [])} className="underline">
                {e.documentTitle} {articleTitle(e.articleNo, e.heading)}
              </Link>{" "}
              <span className="text-muted-foreground">
                {e.versionLabel}
                {!e.current && "（いまは改定されています）"}
              </span>
            </li>
          ))}
        </ol>
      )}
      {message.hiddenEvidenceCount > 0 && (
        <p className="text-sm text-muted-foreground">
          いまは見られない根拠が {message.hiddenEvidenceCount} 件あります。
        </p>
      )}
    </li>
  );
}
