"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { ContactCard } from "@/components/contact-card";
import { Markdown } from "@/components/markdown";
import { QueryState } from "@/components/query-state";
import { apiFetch } from "@/lib/api/http";
import type { DocumentDetail } from "@/lib/api/types";
import { articleTitle, sectionAnchor } from "@/lib/content";
import { useScrollToHash } from "@/lib/scroll-to-hash";

/** 文書（規程）の詳細。目次・条文・版の切り替え・この文書を根拠にする制度と窓口 */
export function DocumentView() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const version = searchParams.get("version");
  const terms = searchParams.getAll("hl");

  const query = new URLSearchParams(version ? { version } : {}).toString();
  const { data, isPending, error } = useQuery({
    queryKey: ["document", id, version],
    queryFn: () => apiFetch<DocumentDetail>(`/api/v1/documents/${encodeURIComponent(id)}${query ? `?${query}` : ""}`),
  });
  useScrollToHash(data !== undefined);

  if (!data) {
    return (
      <main className="mx-auto w-full max-w-5xl px-4 py-8">
        <QueryState isPending={isPending} error={error} />
      </main>
    );
  }

  const { document, sections } = data;
  return (
    <main className="mx-auto grid w-full max-w-5xl gap-8 px-4 py-8 lg:grid-cols-[1fr_18rem]">
      <article className="flex min-w-0 flex-col gap-6">
        <header className="flex flex-col gap-2">
          <p className="text-sm text-muted-foreground">
            {document.category.name}・所管: {document.ownerDepartment.name}
          </p>
          <h1 className="text-2xl font-bold">{document.title}</h1>
          <p className="text-sm">
            {data.version.versionLabel}（{data.version.effectiveFrom} 施行）
          </p>
          {!data.version.current && (
            <p role="note" className="rounded-md border border-amber-500 bg-amber-50 p-3 text-sm dark:bg-amber-950">
              これは過去の版です。現在の内容は{" "}
              <Link className="underline" href={`/documents/${document.id}`}>
                最新の版
              </Link>
              を確認してください。
            </p>
          )}
          {document.summary && <p className="text-muted-foreground">{document.summary}</p>}
        </header>

        {sections.length > 0 && (
          <nav aria-label="目次" className="rounded-lg border border-border p-4">
            <h2 className="mb-2 font-semibold">目次</h2>
            <ol className="grid gap-1 text-sm sm:grid-cols-2">
              {sections.map((s) => (
                <li key={s.id}>
                  <a className="hover:underline" href={`#${sectionAnchor(s.id)}`}>
                    {articleTitle(s.articleNo, s.heading)}
                  </a>
                </li>
              ))}
            </ol>
          </nav>
        )}

        <div className="flex flex-col gap-6">
          {sections.map((s) => (
            <section key={s.id} id={sectionAnchor(s.id)} className="scroll-mt-4">
              <h2 className="mb-1 font-semibold">{articleTitle(s.articleNo, s.heading)}</h2>
              <Markdown text={s.content} terms={terms} />
            </section>
          ))}
        </div>
      </article>

      <aside className="flex flex-col gap-6">
        {data.versions.length > 1 && (
          <section aria-labelledby="versions-heading">
            <h2 id="versions-heading" className="mb-2 font-semibold">
              版の履歴
            </h2>
            <ol className="flex flex-col gap-2 text-sm">
              {data.versions.map((v) => (
                <li key={v.id}>
                  {v.id === data.version.id ? (
                    <span aria-current="page" className="font-semibold">
                      {v.versionLabel}（{v.effectiveFrom}）
                    </span>
                  ) : (
                    <Link
                      className="underline"
                      href={v.current ? `/documents/${document.id}` : `/documents/${document.id}?version=${v.id}`}
                    >
                      {v.versionLabel}（{v.effectiveFrom}）
                    </Link>
                  )}
                  {v.current && <span className="ml-1 text-muted-foreground">現在の版</span>}
                  {v.changeNote && <p className="text-muted-foreground">{v.changeNote}</p>}
                </li>
              ))}
            </ol>
          </section>
        )}

        {data.programs.length > 0 && (
          <section aria-labelledby="programs-heading">
            <h2 id="programs-heading" className="mb-2 font-semibold">
              この規程に基づく制度
            </h2>
            <ul className="flex flex-col gap-2 text-sm">
              {data.programs.map((p) => (
                <li key={p.id}>
                  <Link className="font-medium underline" href={`/programs/${p.id}`}>
                    {p.name}
                  </Link>
                  {p.articleNos.length > 0 && (
                    <span className="ml-1 text-muted-foreground">{p.articleNos.join("・")}</span>
                  )}
                </li>
              ))}
            </ul>
          </section>
        )}

        {data.contacts.length > 0 && (
          <section aria-labelledby="contacts-heading" className="flex flex-col gap-2">
            <h2 id="contacts-heading" className="font-semibold">
              問い合わせ先
            </h2>
            {data.contacts.map((c) => (
              <ContactCard key={c.code} contact={c} />
            ))}
          </section>
        )}
      </aside>
    </main>
  );
}
