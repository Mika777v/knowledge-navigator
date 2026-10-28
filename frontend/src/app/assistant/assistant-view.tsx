"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { MAX_QUERY_LENGTH } from "@/components/search-form";
import type { AssistantAnswer, AssistantEvidence, NavigationResult } from "@/lib/api/types";
import { askAssistant } from "@/lib/assistant/stream";
import { articleTitle } from "@/lib/content";
import { Results, sectionHref } from "../search/search-results";

/** 回答を出さなかった理由ごとの案内。窓口は下の検索結果に出ている（ADR-0009） */
export const UNANSWERABLE_MESSAGES: Record<string, string> = {
  DISABLED: "AI による回答は、いまは使えない設定になっています。下の検索結果と問い合わせ先をご覧ください。",
  NO_EVIDENCE: "規程の中に、この質問の答えが見つかりませんでした。下の問い合わせ先にご相談ください。",
  NOT_IN_CONTEXT: "規程の中に、この質問の答えが見つかりませんでした。下の問い合わせ先にご相談ください。",
};
const DEFAULT_UNANSWERABLE =
  "根拠を確かめられる回答を作れませんでした。下の検索結果と問い合わせ先をご覧ください。";

type State =
  | { phase: "searching" }
  | { phase: "generating"; results: NavigationResult }
  | { phase: "answered"; results: NavigationResult; answer: AssistantAnswer }
  | { phase: "unanswerable"; results: NavigationResult; reason: string }
  | { phase: "error"; results?: NavigationResult };

/**
 * 根拠付き回答（E14-T5・ADR-0013）。検索の4ブロックを先に出し、確かめた回答文が届いたら上に出す。
 * 回答文の [n] は、下の「根拠」の n 番目（DB の値で描いた条）につながる。
 */
export function AssistantView() {
  const q = useSearchParams().get("q")?.trim() ?? "";
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
      <QuestionForm key={q} initialQuery={q} />
      {!q ? (
        <p className="text-muted-foreground">知りたいことを入力してください。規程を根拠に、AI が要点をまとめます。</p>
      ) : (
        // 質問が変わったら、前の回答の状態を捨てて読み直す
        <AnswerStream key={q} question={q} />
      )}
    </main>
  );
}

function QuestionForm({ initialQuery }: { initialQuery: string }) {
  const router = useRouter();
  const [q, setQ] = useState(initialQuery);
  return (
    <form
      role="search"
      className="flex gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        const value = q.trim();
        if (value) router.push(`/assistant?q=${encodeURIComponent(value)}`);
      }}
    >
      <label htmlFor="assistant-q" className="sr-only">
        AI に質問する
      </label>
      <input
        id="assistant-q"
        value={q}
        maxLength={MAX_QUERY_LENGTH}
        onChange={(e) => setQ(e.target.value)}
        placeholder="例: 結婚したらお祝い金はいくらもらえますか？"
        className="min-w-0 flex-1 rounded-md border border-border bg-background px-3 py-2 text-base"
      />
      <button
        type="submit"
        disabled={!q.trim()}
        className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        質問する
      </button>
    </form>
  );
}

function AnswerStream({ question }: { question: string }) {
  const [state, setState] = useState<State>({ phase: "searching" });

  useEffect(() => {
    const controller = new AbortController();
    let results: NavigationResult | undefined;
    askAssistant(
      question,
      (event) => {
        if (event.type === "results") {
          results = event.data;
          setState({ phase: "generating", results: event.data });
        } else if (event.type === "answer" && results) {
          setState({ phase: "answered", results, answer: event.data });
        } else if (event.type === "unanswerable" && results) {
          setState({ phase: "unanswerable", results, reason: event.data.reason });
        }
      },
      controller.signal,
    ).catch(() => {
      // 検索結果まで届いていれば、それは出したままにする
      if (!controller.signal.aborted) setState({ phase: "error", results });
    });
    return () => controller.abort();
  }, [question]);

  const results = "results" in state ? state.results : undefined;
  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="answer-heading" className="flex flex-col gap-3 rounded-lg border border-border p-4">
        <h2 id="answer-heading" className="text-lg font-semibold">
          AI の回答
        </h2>
        <AnswerBody state={state} terms={results?.query.terms ?? []} />
        <p className="text-xs text-muted-foreground">
          AI による要約です。誤りを含むことがあります。最終的な判断は、根拠の規程と担当部署に確認してください。
        </p>
      </section>
      {results && <Results result={results} />}
      <Link href={`/search?q=${encodeURIComponent(question)}`} className="self-start text-sm underline">
        検索結果だけを見る
      </Link>
    </div>
  );
}

function AnswerBody({ state, terms }: { state: State; terms: readonly string[] }) {
  switch (state.phase) {
    case "searching":
      return <p role="status">規程を探しています…</p>;
    case "generating":
      return <p role="status">回答を作っています。数十秒かかることがあります。下の検索結果を先にご覧いただけます。</p>;
    case "unanswerable":
      return <p role="status">{UNANSWERABLE_MESSAGES[state.reason] ?? DEFAULT_UNANSWERABLE}</p>;
    case "error":
      return <p role="alert">回答を取得できませんでした。時間をおいて再度お試しください。</p>;
    case "answered":
      return (
        <>
          <p className="leading-relaxed">
            <AnswerText text={state.answer.answer} evidence={state.answer.evidence} />
          </p>
          <EvidenceList evidence={state.answer.evidence} terms={terms} />
        </>
      );
  }
}

/** 回答文はプレーンテキストとして出し、[n] だけを根拠へのリンクにする（HTML として解釈しない） */
export function AnswerText({ text, evidence }: { text: string; evidence: readonly AssistantEvidence[] }) {
  const refs = new Set(evidence.map((e) => e.ref));
  return (
    <>
      {text.split(/(\[\d+\])/).map((part, i) => {
        const m = /^\[(\d+)\]$/.exec(part);
        if (!m || !refs.has(Number(m[1]))) return <span key={i}>{part}</span>;
        return (
          <sup key={i}>
            <a href={`#evidence-${m[1]}`} className="underline" aria-label={`根拠 ${m[1]}`}>
              [{m[1]}]
            </a>
          </sup>
        );
      })}
    </>
  );
}

function EvidenceList({ evidence, terms }: { evidence: readonly AssistantEvidence[]; terms: readonly string[] }) {
  return (
    <div aria-labelledby="evidence-heading" role="region" className="flex flex-col gap-1">
      <h3 id="evidence-heading" className="text-sm font-semibold">
        根拠
      </h3>
      <ol className="flex flex-col gap-1 text-sm">
        {evidence.map((e) => (
          <li key={e.ref} id={`evidence-${e.ref}`}>
            [{e.ref}]{" "}
            <Link href={sectionHref(e.documentId, e.sectionId, terms)} className="underline">
              {e.documentTitle} {articleTitle(e.articleNo, e.heading)}
            </Link>{" "}
            <span className="text-muted-foreground">
              {e.versionLabel}
              {e.pageFrom != null && `・p.${e.pageFrom}`}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
