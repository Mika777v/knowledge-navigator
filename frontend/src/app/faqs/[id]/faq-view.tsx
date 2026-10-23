"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { BasisLink } from "@/components/basis-link";
import { ContactCard } from "@/components/contact-card";
import { Markdown } from "@/components/markdown";
import { QueryState } from "@/components/query-state";
import { apiFetch } from "@/lib/api/http";
import type { FaqDetail } from "@/lib/api/types";

/** FAQ の詳細。回答と、関連する制度・条文・問い合わせ先 */
export function FaqView() {
  const { id } = useParams<{ id: string }>();
  const { data, isPending, error } = useQuery({
    queryKey: ["faq", id],
    queryFn: () => apiFetch<FaqDetail>(`/api/v1/faqs/${encodeURIComponent(id)}`),
  });

  if (!data) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8">
        <QueryState isPending={isPending} error={error} />
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <header className="flex flex-col gap-2">
        <p className="text-sm text-muted-foreground">
          <Link href="/faqs" className="underline">
            よくある質問
          </Link>
          {data.faq.category && `・${data.faq.category.name}`}
        </p>
        <h1 className="text-2xl font-bold">{data.faq.question}</h1>
      </header>
      <Markdown text={data.answerMarkdown} />

      {data.programs.length > 0 && (
        <section aria-labelledby="programs-heading">
          <h2 id="programs-heading" className="mb-2 font-semibold">
            関連する制度
          </h2>
          <ul className="flex list-disc flex-col gap-1 pl-5">
            {data.programs.map((p) => (
              <li key={p.id}>
                <Link className="underline" href={`/programs/${p.id}`}>
                  {p.name}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {data.references.length > 0 && (
        <section aria-labelledby="references-heading">
          <h2 id="references-heading" className="mb-2 font-semibold">
            根拠となる規程
          </h2>
          <ul className="flex list-disc flex-col gap-1 pl-5">
            {data.references.map((b, i) => (
              <li key={`${b.documentId}-${b.sectionId ?? i}`}>
                <BasisLink basis={b} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {data.contact && (
        <section aria-labelledby="contact-heading" className="flex flex-col gap-2">
          <h2 id="contact-heading" className="font-semibold">
            問い合わせ先
          </h2>
          <ContactCard contact={data.contact} />
        </section>
      )}
    </main>
  );
}
