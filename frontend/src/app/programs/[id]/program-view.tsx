"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { BasisLink } from "@/components/basis-link";
import { ContactCard } from "@/components/contact-card";
import { QueryState } from "@/components/query-state";
import { apiFetch } from "@/lib/api/http";
import type { ProgramDetail } from "@/lib/api/types";

/** 制度の詳細。対象者・内容・申請方法・期限・必要書類と、根拠の条文・窓口・関連 FAQ */
export function ProgramView() {
  const { id } = useParams<{ id: string }>();
  const { data, isPending, error } = useQuery({
    queryKey: ["program", id],
    queryFn: () => apiFetch<ProgramDetail>(`/api/v1/programs/${encodeURIComponent(id)}`),
  });

  if (!data) {
    return (
      <main className="mx-auto w-full max-w-5xl px-4 py-8">
        <QueryState isPending={isPending} error={error} />
      </main>
    );
  }

  const { program } = data;
  const facts: [string, string | null | undefined][] = [
    ["対象者", data.eligibility],
    ["内容", program.benefit],
    ["申請方法", data.howToApply],
    ["期限", program.deadlineNote],
    ["必要書類", data.requiredDocuments],
  ];
  return (
    <main className="mx-auto grid w-full max-w-5xl gap-8 px-4 py-8 lg:grid-cols-[1fr_18rem]">
      <article className="flex min-w-0 flex-col gap-6">
        <header className="flex flex-col gap-2">
          <p className="text-sm text-muted-foreground">
            {program.category?.name ?? "制度"}・所管: {data.ownerDepartment.name}
          </p>
          <h1 className="text-2xl font-bold">{program.name}</h1>
          <p>{program.summary}</p>
          {data.lifeEvents.length > 0 && (
            <ul aria-label="きっかけ" className="flex flex-wrap gap-2">
              {data.lifeEvents.map((e) => (
                <li key={e.code} className="rounded-full border border-border px-2 py-0.5 text-xs">
                  {e.name}
                </li>
              ))}
            </ul>
          )}
        </header>

        <dl className="grid gap-x-6 gap-y-3 rounded-lg border border-border p-4 sm:grid-cols-[8rem_1fr]">
          {facts
            .filter(([, value]) => value)
            .map(([label, value]) => (
              <div key={label} className="contents">
                <dt className="font-semibold">{label}</dt>
                <dd className="whitespace-pre-wrap">{value}</dd>
              </div>
            ))}
        </dl>

        {data.basis.length > 0 && (
          <section aria-labelledby="basis-heading">
            <h2 id="basis-heading" className="mb-2 font-semibold">
              根拠となる規程
            </h2>
            <ul className="flex list-disc flex-col gap-1 pl-5">
              {data.basis.map((b, i) => (
                <li key={`${b.documentId}-${b.articleNo ?? i}`}>
                  <BasisLink basis={b} />
                </li>
              ))}
            </ul>
          </section>
        )}

        {data.faqs.length > 0 && (
          <section aria-labelledby="faqs-heading">
            <h2 id="faqs-heading" className="mb-2 font-semibold">
              よくある質問
            </h2>
            <ul className="flex list-disc flex-col gap-1 pl-5">
              {data.faqs.map((f) => (
                <li key={f.id}>
                  <Link className="underline" href={`/faqs/${f.id}`}>
                    {f.question}
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}
      </article>

      <aside className="flex flex-col gap-2" aria-labelledby="contacts-heading">
        <h2 id="contacts-heading" className="font-semibold">
          申請・問い合わせ先
        </h2>
        {data.contacts.length > 0 ? (
          data.contacts.map((c) => <ContactCard key={`${c.code}-${c.role}`} contact={c} />)
        ) : (
          <p className="text-sm text-muted-foreground">窓口が登録されていません。</p>
        )}
      </aside>
    </main>
  );
}
