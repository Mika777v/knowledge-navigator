"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { BasisLink } from "@/components/basis-link";
import { ContactCard } from "@/components/contact-card";
import { Highlight } from "@/components/highlight";
import { QueryState } from "@/components/query-state";
import { SearchForm } from "@/components/search-form";
import { apiFetch } from "@/lib/api/http";
import type { NavigationResult } from "@/lib/api/types";
import { articleTitle, sectionAnchor } from "@/lib/content";
import { recordClick } from "@/lib/search/log";
import { Feedback } from "./feedback";

/** 条文へのリンク。検索語を強調して表示できるよう ?hl= を付ける */
export function sectionHref(documentId: string, sectionId: string, terms: readonly string[]) {
  const params = new URLSearchParams(terms.map((t) => ["hl", t]));
  const query = params.toString();
  return `/documents/${documentId}${query ? `?${query}` : ""}#${sectionAnchor(sectionId)}`;
}

/**
 * 検索結果（docs/ARCHITECTURE.md §3.3）。使える制度・根拠となる規程・よくある質問・問い合わせ先の4ブロックで表示する。
 * 狭い画面では「制度 → 窓口 → 規程 → FAQ」の順に並べる（まず何が使えて、どこに聞けばよいかを先に見せる）。
 */
export function SearchResults() {
  const q = useSearchParams().get("q")?.trim() ?? "";
  const { data, isPending, error } = useQuery({
    queryKey: ["navigate", q],
    queryFn: () => apiFetch<NavigationResult>(`/api/v1/navigate?q=${encodeURIComponent(q)}`),
    enabled: q.length > 0,
    // 検索ごとに検索ログが記録されるため、同じ画面の再表示で検索し直さない
    staleTime: Infinity,
  });

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
      {/* 検索語が変わったら入力欄も作り直す */}
      <SearchForm key={q} initialQuery={q} />
      {!q ? (
        <p className="text-muted-foreground">知りたいことを入力してください。</p>
      ) : !data ? (
        <QueryState isPending={isPending} error={error} />
      ) : (
        <Results result={data} />
      )}
    </main>
  );
}

function Results({ result }: { result: NavigationResult }) {
  const { query, searchLogId } = result;
  const terms = query.terms;
  const click = (type: Parameters<typeof recordClick>[1], id: string, rank: number) => () =>
    recordClick(searchLogId, type, id, rank);

  return (
    <div className="flex flex-col gap-6">
      {query.matchedLifeEvents.length > 0 && (
        <p className="text-sm">
          「{query.matchedLifeEvents.map((e) => e.name).join("」「")}」のときに使える制度と手続きを表示しています。
        </p>
      )}
      {result.fallback && (
        <p role="status" className="rounded-md border border-border p-3">
          該当する制度・規程が見つかりませんでした。言葉を変えて検索するか、下の窓口に相談してください。
        </p>
      )}

      <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
        {result.programs.length > 0 && (
          <section aria-labelledby="programs-heading" className="flex flex-col gap-3 lg:col-start-1">
            <h2 id="programs-heading" className="text-lg font-semibold">
              使える制度
            </h2>
            <ul className="flex flex-col gap-3">
              {result.programs.map((p, i) => (
                <li key={p.id} className="flex flex-col gap-1 rounded-lg border border-border p-4">
                  <Link
                    href={`/programs/${p.id}`}
                    onClick={click("PROGRAM", p.id, i + 1)}
                    className="text-lg font-semibold underline"
                  >
                    {p.name}
                  </Link>
                  <p>{p.summary}</p>
                  <dl className="grid grid-cols-[auto_1fr] gap-x-3 text-sm">
                    {p.benefit && (
                      <>
                        <dt className="text-muted-foreground">内容</dt>
                        <dd>{p.benefit}</dd>
                      </>
                    )}
                    {p.deadlineNote && (
                      <>
                        <dt className="text-muted-foreground">期限</dt>
                        <dd>{p.deadlineNote}</dd>
                      </>
                    )}
                  </dl>
                  {p.basis.length > 0 && (
                    <p className="text-sm">
                      根拠:{" "}
                      {p.basis.map((b, j) => (
                        <span key={`${b.documentId}-${b.articleNo ?? j}`}>
                          {j > 0 && "、"}
                          <BasisLink basis={b} onClick={click("DOCUMENT", b.documentId, i + 1)} />
                        </span>
                      ))}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          </section>
        )}

        <section
          aria-labelledby="contacts-heading"
          className="flex flex-col gap-3 lg:col-start-2 lg:row-span-3 lg:row-start-1"
        >
          <h2 id="contacts-heading" className="text-lg font-semibold">
            問い合わせ先
          </h2>
          {result.contacts.map((c, i) => (
            // 窓口はメール・チャットのリンクを押したときだけ記録する
            <div
              key={c.code}
              onClickCapture={(e) => {
                if ((e.target as HTMLElement).closest("a")) recordClick(searchLogId, "CONTACT", c.id, i + 1);
              }}
            >
              <ContactCard contact={c} note={c.reason} />
            </div>
          ))}
        </section>

        {result.documents.length > 0 && (
          <section aria-labelledby="documents-heading" className="flex flex-col gap-3 lg:col-start-1">
            <h2 id="documents-heading" className="text-lg font-semibold">
              根拠となる規程
            </h2>
            <ul className="flex flex-col gap-3">
              {result.documents.map((d, i) => (
                <li key={d.id} className="flex flex-col gap-2 rounded-lg border border-border p-4">
                  <div className="flex flex-wrap items-baseline gap-2">
                    <Link
                      href={`/documents/${d.id}`}
                      onClick={click("DOCUMENT", d.id, i + 1)}
                      className="font-semibold underline"
                    >
                      {d.title}
                    </Link>
                    <span className="text-sm text-muted-foreground">
                      {d.versionLabel}（{d.effectiveFrom} 施行）
                    </span>
                  </div>
                  <ul className="flex flex-col gap-2 text-sm">
                    {d.sections.map((s) => (
                      <li key={s.id}>
                        <Link
                          href={sectionHref(d.id, s.id, terms)}
                          onClick={click("SECTION", s.id, i + 1)}
                          className="font-medium underline"
                        >
                          {articleTitle(s.articleNo, s.heading) || "条文"}
                        </Link>
                        <p className="text-muted-foreground">
                          <Highlight text={s.snippet} terms={terms} />
                        </p>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </section>
        )}

        {result.faqs.length > 0 && (
          <section aria-labelledby="faqs-heading" className="flex flex-col gap-3 lg:col-start-1">
            <h2 id="faqs-heading" className="text-lg font-semibold">
              よくある質問
            </h2>
            <ul className="flex list-disc flex-col gap-1 pl-5">
              {result.faqs.map((f, i) => (
                <li key={f.id}>
                  <Link href={`/faqs/${f.id}`} onClick={click("FAQ", f.id, i + 1)} className="underline">
                    {f.question}
                  </Link>
                </li>
              ))}
            </ul>
            <Link href="/faqs" className="self-start text-sm underline">
              よくある質問をすべて見る
            </Link>
          </section>
        )}
      </div>

      {result.relatedPrograms.length > 0 && (
        <section aria-labelledby="related-heading" className="flex flex-col gap-2">
          <h2 id="related-heading" className="font-semibold">
            あわせて確認したい手続き
          </h2>
          <ul className="flex flex-wrap gap-2">
            {result.relatedPrograms.map((p) => (
              <li key={p.id}>
                <Link href={`/programs/${p.id}`} className="block rounded-full border border-border px-3 py-1 text-sm">
                  {p.name}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {searchLogId && <Feedback key={searchLogId} searchLogId={searchLogId} />}
    </div>
  );
}
