"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { ifMatch, STATUS_LABELS } from "@/lib/content-labels";
import {
  type AdminDocument,
  adminDocumentsKey,
  type DocumentFormValues,
  documentPath,
  errorMessage,
  toFormValues,
  toUpdateRequest,
} from "../document-api";
import { DocumentForm } from "../document-form";
import { VersionsPanel } from "./versions-panel";

/** 文書の編集。上で文書の情報を、下で版（本文）を管理する */
export function EditDocument() {
  const { id } = useParams<{ id: string }>();
  const { data, error } = useQuery({
    queryKey: [...adminDocumentsKey, id],
    queryFn: () => apiFetch<AdminDocument>(documentPath(id)),
  });
  if (error) return <p role="alert">{errorMessage(error)}</p>;
  if (!data) return <p className="text-muted-foreground">読み込み中…</p>;
  return <DocumentEditor key={data.id} document={data} />;
}

function DocumentEditor({ document: loaded }: { document: AdminDocument }) {
  const queryClient = useQueryClient();
  // 最後に保存された状態（版番号・公開状態）。保存・公開のたびに更新し、次の If-Match に使う
  const [document, setDocument] = useState(loaded);
  const [values, setValues] = useState<DocumentFormValues>(() => toFormValues(loaded));
  const [message, setMessage] = useState<string | null>(null);

  const onChanged = (updated: AdminDocument, text?: string) => {
    setDocument(updated);
    queryClient.setQueryData([...adminDocumentsKey, updated.id], updated);
    void queryClient.invalidateQueries({ queryKey: adminDocumentsKey, exact: true });
    if (text) setMessage(text);
  };
  const save = useMutation({
    mutationFn: () =>
      apiFetch<AdminDocument>(documentPath(document.id), {
        method: "PUT",
        headers: ifMatch(document.rowVersion),
        json: toUpdateRequest(values),
      }),
    onSuccess: (updated) => onChanged(updated, "文書の情報を保存しました。"),
  });
  const archive = useMutation({
    mutationFn: () =>
      apiFetch<AdminDocument>(`${documentPath(document.id)}/archive`, {
        method: "POST",
        headers: ifMatch(document.rowVersion),
      }),
    onSuccess: (updated) => onChanged(updated, "公開を終了しました。"),
  });
  const error = save.error ?? archive.error;

  return (
    <div className="flex flex-col gap-8">
      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          setMessage(null);
          archive.reset();
          save.mutate();
        }}
      >
        <div className="flex flex-wrap items-baseline gap-3">
          <h1 className="text-2xl font-bold">文書の編集</h1>
          <span className="font-mono text-sm">{document.code}</span>
          <span className="rounded bg-foreground/10 px-2 py-0.5 text-sm">{STATUS_LABELS[document.status]}</span>
          {document.status === "PUBLISHED" && (
            <Link href={`/documents/${document.id}`} className="text-sm underline">
              利用者向けの画面で見る
            </Link>
          )}
        </div>
        <p className="text-sm text-muted-foreground">所管部署: {document.ownerDepartment?.name}</p>
        <DocumentForm values={values} onChange={setValues} withTags />
        {message && (
          <p role="status" className="text-sm">
            {message}
          </p>
        )}
        {error && (
          <p role="alert" className="text-sm text-red-600 dark:text-red-400">
            {errorMessage(error)}
          </p>
        )}
        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={save.isPending}
            className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
          >
            文書の情報を保存
          </button>
          {document.status === "PUBLISHED" && (
            <button
              type="button"
              disabled={archive.isPending}
              onClick={() => {
                if (!window.confirm("この文書の公開を終了しますか？利用者には表示されなくなります。")) return;
                setMessage(null);
                save.reset();
                archive.mutate();
              }}
              className="rounded-md border border-border px-4 py-2"
            >
              公開を終了する
            </button>
          )}
        </div>
      </form>

      <VersionsPanel document={document} onPublished={(updated) => onChanged(updated)} />
    </div>
  );
}
