"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import type { FeedbackSummary, QueryReport } from "@/lib/api/types";
import { formatDateTime, formatRatio } from "@/lib/format";

const BASE = "/api/v1/admin/analytics";

type Period = { from: string; to: string };

function periodQuery(period: Period) {
  const params = new URLSearchParams({ limit: "20" });
  if (period.from) params.set("from", period.from);
  if (period.to) params.set("to", period.to);
  return params.toString();
}

function ErrorText({ error }: { error: unknown }) {
  return (
    <p role="alert" className="text-sm">
      {error instanceof ApiError ? error.detail : "表示できませんでした。時間をおいて再度お試しください。"}
    </p>
  );
}

/**
 * 検索の分析。何も見つからなかった質問から FAQ・辞書の改善候補を見つけ、よく検索される質問とフィードバックで効果を確かめる。
 * 個人の検索履歴にならないよう、質問ごとの集計は異なる利用者が一定数以上のものだけが API から返る。
 */
export function AnalyticsDashboard() {
  const [draft, setDraft] = useState<Period>({ from: "", to: "" });
  const [period, setPeriod] = useState<Period>({ from: "", to: "" });
  const query = periodQuery(period);

  const zeroHits = useQuery({
    queryKey: ["admin", "analytics", "zero-hits", query],
    queryFn: () => apiFetch<QueryReport>(`${BASE}/zero-hits?${query}`),
  });
  const topQueries = useQuery({
    queryKey: ["admin", "analytics", "top-queries", query],
    queryFn: () => apiFetch<QueryReport>(`${BASE}/top-queries?${query}`),
  });
  const feedback = useQuery({
    queryKey: ["admin", "analytics", "feedback", query],
    queryFn: () => apiFetch<FeedbackSummary>(`${BASE}/feedback?${query}`),
  });
  const shown = zeroHits.data ?? topQueries.data;

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">検索の分析</h1>
        <form
          className="flex flex-wrap items-end gap-2 text-sm"
          onSubmit={(e) => {
            e.preventDefault();
            setPeriod(draft);
          }}
        >
          <label className="flex flex-col gap-1">
            開始日
            <input
              type="date"
              value={draft.from}
              onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              className="rounded-md border border-border bg-background px-2 py-1"
            />
          </label>
          <label className="flex flex-col gap-1">
            終了日
            <input
              type="date"
              value={draft.to}
              onChange={(e) => setDraft({ ...draft, to: e.target.value })}
              className="rounded-md border border-border bg-background px-2 py-1"
            />
          </label>
          <button type="submit" className="rounded-md border border-border px-3 py-1">
            表示
          </button>
        </form>
        {shown && (
          <p className="text-sm text-muted-foreground">
            {shown.period.from}〜{shown.period.to}（日本時間）。個人の検索履歴にならないよう、{shown.minDistinctUsers}
            人以上が検索した質問だけを表示しています。
          </p>
        )}
      </header>

      <section aria-labelledby="zero-hits-heading" className="flex flex-col gap-2">
        <h2 id="zero-hits-heading" className="text-lg font-semibold">
          何も見つからなかった質問
        </h2>
        <p className="text-sm text-muted-foreground">FAQ やキーワード辞書に追加する候補です。</p>
        {zeroHits.error ? (
          <ErrorText error={zeroHits.error} />
        ) : !zeroHits.data ? (
          <p className="text-sm text-muted-foreground">読み込み中…</p>
        ) : zeroHits.data.queries.length === 0 ? (
          <p className="text-sm">該当する質問はありません。</p>
        ) : (
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="py-1 pr-3">質問</th>
                <th className="py-1 pr-3 text-right">回数</th>
                <th className="py-1 pr-3 text-right">人数</th>
                <th className="py-1 pr-3">最後に検索された日時</th>
                <th className="py-1">
                  <span className="sr-only">操作</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {zeroHits.data.queries.map((q) => (
                <tr key={q.query} className="border-b border-border">
                  <td className="py-1 pr-3">{q.query}</td>
                  <td className="py-1 pr-3 text-right">{q.zeroHitSearches}</td>
                  <td className="py-1 pr-3 text-right">{q.users}</td>
                  <td className="py-1 pr-3">{formatDateTime(q.lastSearchedAt)}</td>
                  <td className="py-1 text-right">
                    <Link href={`/admin/dictionary?term=${encodeURIComponent(q.query)}`} className="underline">
                      辞書で確認
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section aria-labelledby="top-heading" className="flex flex-col gap-2">
        <h2 id="top-heading" className="text-lg font-semibold">
          よく検索された質問
        </h2>
        {topQueries.error ? (
          <ErrorText error={topQueries.error} />
        ) : !topQueries.data ? (
          <p className="text-sm text-muted-foreground">読み込み中…</p>
        ) : topQueries.data.queries.length === 0 ? (
          <p className="text-sm">該当する質問はありません。</p>
        ) : (
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="py-1 pr-3">質問</th>
                <th className="py-1 pr-3 text-right">回数</th>
                <th className="py-1 pr-3 text-right">人数</th>
                <th className="py-1 pr-3 text-right" title="何も見つからなかった検索の割合">
                  ゼロヒット
                </th>
                <th className="py-1 text-right" title="結果のどれかが押された検索の割合">
                  クリック率
                </th>
              </tr>
            </thead>
            <tbody>
              {topQueries.data.queries.map((q) => (
                <tr key={q.query} className="border-b border-border">
                  <td className="py-1 pr-3">
                    <Link href={`/search?q=${encodeURIComponent(q.query)}`} className="underline">
                      {q.query}
                    </Link>
                  </td>
                  <td className="py-1 pr-3 text-right">{q.searches}</td>
                  <td className="py-1 pr-3 text-right">{q.users}</td>
                  <td className="py-1 pr-3 text-right">{formatRatio(q.zeroHitSearches, q.searches)}</td>
                  <td className="py-1 text-right">{formatRatio(q.clickedSearches, q.searches)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section aria-labelledby="feedback-heading" className="flex flex-col gap-2">
        <h2 id="feedback-heading" className="text-lg font-semibold">
          フィードバック
        </h2>
        {feedback.error ? (
          <ErrorText error={feedback.error} />
        ) : !feedback.data ? (
          <p className="text-sm text-muted-foreground">読み込み中…</p>
        ) : (
          <>
            <p className="text-sm">
              役に立った: <strong>{feedback.data.helpful}</strong> 件 ／ 役に立たなかった:{" "}
              <strong>{feedback.data.notHelpful}</strong> 件（役に立った割合{" "}
              {formatRatio(feedback.data.helpful, feedback.data.helpful + feedback.data.notHelpful)}）
            </p>
            {feedback.data.comments.length > 0 && (
              <ul className="flex flex-col gap-2 text-sm">
                {feedback.data.comments.map((c, i) => (
                  <li key={`${c.createdAt}-${i}`} className="rounded-md border border-border p-3">
                    <p className="text-muted-foreground">
                      {formatDateTime(c.createdAt)}・「{c.query}」・{c.helpful ? "役に立った" : "役に立たなかった"}
                    </p>
                    <p className="whitespace-pre-wrap">{c.comment}</p>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </section>
    </div>
  );
}
