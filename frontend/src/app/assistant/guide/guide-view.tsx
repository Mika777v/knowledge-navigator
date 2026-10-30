"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { BasisLink } from "@/components/basis-link";
import { ContactCard } from "@/components/contact-card";
import { apiFetch } from "@/lib/api/http";
import type { GuideChecklistItem, GuideProgram, GuideQuestion, GuideResult } from "@/lib/api/types";

export const STATUS_LABELS: Record<GuideProgram["status"], string> = {
  ELIGIBLE: "対象",
  UNKNOWN: "要確認",
  NOT_ELIGIBLE: "対象外",
};

/**
 * きっかけの案内（ADR-0014 の決定 1・E15-T2〜T4）。確認の質問に答えると、制度を対象・要確認・対象外に分け、
 * 手続きのチェックリストを出す。判定・根拠・窓口はすべて backend が DB の値から作る（LLM は使わない）。
 * 答えとチェックの状態は保存しない。
 */
export function GuideView() {
  const event = useSearchParams().get("event")?.trim() ?? "";
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
      {event ? (
        // きっかけが変わったら、答えを捨てて読み直す
        <Guide key={event} event={event} />
      ) : (
        <p className="text-muted-foreground">ホームの「きっかけから探す」から、きっかけを選んでください。</p>
      )}
    </main>
  );
}

function Guide({ event }: { event: string }) {
  // 利用者が選んだ答えだけを持つ。選んでいない質問は、backend がプロフィールから選ぶか未回答にする
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const { data, error, isFetching } = useQuery({
    queryKey: ["assistant-guide", event, answers],
    queryFn: () =>
      apiFetch<GuideResult>("/api/v1/assistant/guides", { method: "POST", json: { lifeEvent: event, answers } }),
    // 答えを変えている間も、前の結果を出したままにする
    placeholderData: keepPreviousData,
  });

  if (error) return <p role="alert">{error.message}</p>;
  if (!data) return <p role="status">読み込み中…</p>;

  const questionText = new Map(data.questions.map((q) => [q.code, q.text]));
  const contactsById = new Map(data.programs.map((p) => [p.id, p.contacts]));
  return (
    <>
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">{data.lifeEvent.name}の手続き</h1>
        {data.lifeEvent.description && <p className="text-muted-foreground">{data.lifeEvent.description}</p>}
      </header>

      {data.questions.length > 0 && (
        <section aria-labelledby="questions-heading" className="flex flex-col gap-3 rounded-lg border border-border p-4">
          <h2 id="questions-heading" className="text-lg font-semibold">
            確認の質問
          </h2>
          {data.questions.map((q) => (
            <QuestionField
              key={q.code}
              question={q}
              // 選んだ答えは、判定し直す前からすぐに出す
              selected={answers[q.code] ?? q.answer}
              onChange={(value) =>
                // 画面に出ている答え（プロフィールから選んだものを含む）を、そのまま利用者の答えとして送る
                setAnswers({ ...currentAnswers(data.questions), ...answers, [q.code]: value })
              }
            />
          ))}
          {isFetching && (
            <p role="status" className="text-sm text-muted-foreground">
              判定し直しています…
            </p>
          )}
        </section>
      )}

      <section aria-labelledby="guide-programs-heading" className="flex flex-col gap-3">
        <h2 id="guide-programs-heading" className="text-lg font-semibold">
          使える制度
        </h2>
        <ul className="flex flex-col gap-3">
          {data.programs.map((p) => (
            <ProgramItem key={p.id} program={p} questionText={questionText} />
          ))}
        </ul>
        <p className="text-xs text-muted-foreground">
          対象かどうかは、答えと規程の条件から機械的に判定した目安です。最終的には、根拠の規程と申請先にご確認ください。
        </p>
      </section>

      <Checklist items={data.checklist} contactsById={contactsById} />
    </>
  );
}

function currentAnswers(questions: readonly GuideQuestion[]): Record<string, string> {
  return Object.fromEntries(questions.flatMap((q) => (q.answer ? [[q.code, q.answer]] : [])));
}

function QuestionField({
  question,
  selected,
  onChange,
}: {
  question: GuideQuestion;
  selected: string | undefined;
  onChange: (value: string) => void;
}) {
  return (
    <fieldset className="flex flex-col gap-1">
      <legend className="font-medium">{question.text}</legend>
      <div className="flex flex-wrap gap-x-4 gap-y-1">
        {question.choices.map((c) => (
          <label key={c.value} className="flex items-center gap-1">
            <input
              type="radio"
              name={question.code}
              value={c.value}
              checked={selected === c.value}
              onChange={() => onChange(c.value)}
            />
            {c.label}
          </label>
        ))}
      </div>
      {question.answerSource === "PROFILE" && selected === question.answer && (
        <p className="text-xs text-muted-foreground">登録されている情報から選んでいます。違う場合は選び直してください。</p>
      )}
    </fieldset>
  );
}

function ProgramItem({ program, questionText }: { program: GuideProgram; questionText: Map<string, string> }) {
  const reasons = (program.status === "NOT_ELIGIBLE" ? program.unmetQuestions : program.unansweredQuestions)
    .map((code) => questionText.get(code))
    .filter((text): text is string => Boolean(text));
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border p-3" aria-label={program.name}>
      <div className="flex flex-wrap items-baseline gap-2">
        <Link className="font-semibold underline" href={`/programs/${program.id}`}>
          {program.name}
        </Link>
        <span className="rounded bg-foreground/10 px-1.5 py-0.5 text-xs">{STATUS_LABELS[program.status]}</span>
        {program.relation === "RELATED" && <span className="text-xs text-muted-foreground">あわせて必要な手続き</span>}
      </div>
      <p className="text-sm">{program.summary}</p>
      {program.eligibility && <p className="text-sm text-muted-foreground">対象: {program.eligibility}</p>}
      {reasons.length > 0 && (
        <p className="text-sm">
          {program.status === "NOT_ELIGIBLE" ? "当てはまらない条件" : "答えると判定できます"}: {reasons.join("／")}
        </p>
      )}
      {program.basis.length > 0 && (
        <p className="text-sm">
          根拠:{" "}
          {program.basis.map((b, i) => (
            <span key={`${b.documentId}-${b.sectionId ?? i}`}>
              {i > 0 && "、"}
              <BasisLink basis={b} />
            </span>
          ))}
        </p>
      )}
    </li>
  );
}

/** 手続きの ToDo。チェックは画面の中だけで、保存しない */
function Checklist({
  items,
  contactsById,
}: {
  items: readonly GuideChecklistItem[];
  contactsById: Map<string, GuideProgram["contacts"]>;
}) {
  const [done, setDone] = useState<Set<string>>(new Set());
  return (
    <section aria-labelledby="checklist-heading" className="flex flex-col gap-3">
      <h2 id="checklist-heading" className="text-lg font-semibold">
        手続きのチェックリスト
      </h2>
      {items.length === 0 ? (
        <p className="text-muted-foreground">いまの答えで対象になる手続きはありません。</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => {
            const apply = (contactsById.get(item.programId) ?? []).filter((c) => c.role === "APPLY");
            return (
              <li key={item.programId} className="flex flex-col gap-2 rounded-lg border border-border p-3">
                <label className="flex items-center gap-2 font-semibold">
                  <input
                    type="checkbox"
                    checked={done.has(item.programId)}
                    onChange={() =>
                      setDone((prev) => {
                        const next = new Set(prev);
                        if (!next.delete(item.programId)) next.add(item.programId);
                        return next;
                      })
                    }
                  />
                  {item.programName}
                  {item.status === "UNKNOWN" && <span className="text-xs font-normal">（要確認）</span>}
                </label>
                <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
                  {item.requiredDocuments && (
                    <>
                      <dt className="text-muted-foreground">必要書類</dt>
                      <dd>{item.requiredDocuments}</dd>
                    </>
                  )}
                  {item.deadlineNote && (
                    <>
                      <dt className="text-muted-foreground">期限</dt>
                      <dd>{item.deadlineNote}</dd>
                    </>
                  )}
                  {item.howToApply && (
                    <>
                      <dt className="text-muted-foreground">申請の方法</dt>
                      <dd>{item.howToApply}</dd>
                    </>
                  )}
                </dl>
                {apply.map((c) => (
                  <ContactCard key={c.code} contact={c} />
                ))}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
