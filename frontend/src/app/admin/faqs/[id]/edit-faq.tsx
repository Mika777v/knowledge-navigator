"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import type { AdminFaq } from "@/lib/api/types";
import { ifMatch, STATUS_LABELS } from "@/lib/content-labels";
import { adminErrorMessage } from "../../admin-errors";
import { adminFaqsKey } from "../faq-list";
import { FaqForm, type FaqFormValues, toContentRequest, toFormValues } from "../faq-form";

/**
 * FAQ の編集・公開・公開終了。更新は楽観ロック（If-Match）で、ほかの人の更新を上書きしない。
 */
export function EditFaq() {
  const { id } = useParams<{ id: string }>();
  const { data: faq, error } = useQuery({
    queryKey: [...adminFaqsKey, id],
    queryFn: () => apiFetch<AdminFaq>(`/api/v1/admin/faqs/${encodeURIComponent(id)}`),
  });
  if (error) return <p role="alert">{adminErrorMessage(error)}</p>;
  if (!faq) return <p className="text-muted-foreground">読み込み中…</p>;
  return <EditFaqForm key={faq.id} faq={faq} />;
}

function EditFaqForm({ faq: loaded }: { faq: AdminFaq }) {
  const queryClient = useQueryClient();
  // 最後に保存された状態（版番号・公開状態）。保存のたびに更新し、次の If-Match に使う
  const [faq, setFaq] = useState(loaded);
  const [values, setValues] = useState<FaqFormValues>(() => toFormValues(loaded));
  const [message, setMessage] = useState<string | null>(null);
  const path = `/api/v1/admin/faqs/${encodeURIComponent(faq.id)}`;

  const onSaved = (updated: AdminFaq, text: string) => {
    setFaq(updated);
    queryClient.setQueryData([...adminFaqsKey, faq.id], updated);
    void queryClient.invalidateQueries({ queryKey: adminFaqsKey, exact: true });
    setMessage(text);
  };
  const save = useMutation({
    mutationFn: () =>
      apiFetch<AdminFaq>(path, { method: "PUT", headers: ifMatch(faq.rowVersion), json: toContentRequest(values) }),
    onSuccess: (updated) => onSaved(updated, "保存しました。"),
  });
  const transition = useMutation({
    mutationFn: async (action: "publish" | "archive") => {
      // 公開するときは、編集中の内容を先に保存してから公開する（保存し忘れた変更が失われないように）
      const current =
        action === "publish"
          ? await apiFetch<AdminFaq>(path, {
              method: "PUT",
              headers: ifMatch(faq.rowVersion),
              json: toContentRequest(values),
            })
          : faq;
      // 保存できて公開に失敗したときも、次の操作は保存後の版で行う
      setFaq(current);
      return apiFetch<AdminFaq>(`${path}/${action}`, { method: "POST", headers: ifMatch(current.rowVersion) });
    },
    onSuccess: (updated, action) => onSaved(updated, action === "publish" ? "公開しました。" : "公開を終了しました。"),
  });
  const error = save.error ?? transition.error;
  const busy = save.isPending || transition.isPending;

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        setMessage(null);
        transition.reset();
        save.mutate();
      }}
    >
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-bold">FAQ の編集</h1>
        <span className="font-mono text-sm">{faq.code}</span>
        <span className="rounded bg-foreground/10 px-2 py-0.5 text-sm">{STATUS_LABELS[faq.status]}</span>
        {faq.status === "PUBLISHED" && (
          <Link href={`/faqs/${faq.id}`} className="text-sm underline">
            利用者向けの画面で見る
          </Link>
        )}
      </div>
      <p className="text-sm text-muted-foreground">所管部署: {faq.ownerDepartment?.name}</p>
      <FaqForm values={values} onChange={setValues} />
      {message && (
        <p role="status" className="text-sm">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {adminErrorMessage(error)}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
        >
          保存
        </button>
        {faq.status !== "PUBLISHED" && (
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setMessage(null);
              save.reset();
              transition.mutate("publish");
            }}
            className="rounded-md border border-border px-4 py-2"
          >
            保存して公開する
          </button>
        )}
        {faq.status === "PUBLISHED" && (
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              if (!window.confirm("この FAQ の公開を終了しますか？利用者には表示されなくなります。")) return;
              setMessage(null);
              save.reset();
              transition.mutate("archive");
            }}
            className="rounded-md border border-border px-4 py-2"
          >
            公開を終了する
          </button>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        公開するには、問い合わせ先か関連する制度が必要です。公開範囲は、関連する規程より広くできません。
      </p>
    </form>
  );
}
