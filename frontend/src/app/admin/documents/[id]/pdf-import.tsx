"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { type AdminDocument, documentPath, errorMessage, type IngestionJob, type StoredFile } from "../document-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";
/** backend の上限（knavi.storage.max-upload-bytes）と同じ */
const MAX_BYTES = 20 * 1024 * 1024;

const STATUS_LABELS: Record<string, string> = {
  QUEUED: "取り込みの順番を待っています…",
  RUNNING: "PDF を読み取っています…",
  SUCCEEDED: "取り込みました。",
  FAILED: "取り込めませんでした。",
};

/**
 * 規程の PDF をアップロードして、下書きの版として取り込む（ADR-0010）。
 * 取り込みは backend で非同期に行うので、状況を2秒ごとに確かめ、終わったらできた版を開く
 */
export function PdfImport({
  document,
  onImported,
  onCancel,
}: {
  document: AdminDocument;
  onImported: (versionId: string) => void;
  onCancel: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [label, setLabel] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [changeNote, setChangeNote] = useState("");
  const [jobId, setJobId] = useState<string | null>(null);
  const tooLarge = !!file && file.size > MAX_BYTES;

  const submit = useMutation({
    mutationFn: async () => {
      const form = new FormData();
      form.append("file", file as File);
      const stored = await apiFetch<StoredFile>("/api/v1/admin/files", { method: "POST", body: form });
      return apiFetch<IngestionJob>(`${documentPath(document.id)}/ingestion-jobs`, {
        method: "POST",
        json: {
          fileId: stored.id,
          versionLabel: label.trim(),
          effectiveFrom,
          changeNote: changeNote.trim() || undefined,
        },
      });
    },
    onSuccess: (job) => setJobId(job.id),
  });

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border p-4 text-sm">
      <h3 className="font-semibold">PDF から版を取り込む</h3>
      <p className="text-muted-foreground">
        規程の PDF から本文を読み取り、条ごとに分けて下書きの版を作ります。条ごとに原本のページも記録します。
        取り込んだ後、内容を確かめてから公開してください（画像だけの PDF やパスワード付きの PDF は取り込めません）。
      </p>
      {jobId ? (
        <JobStatus jobId={jobId} onSucceeded={onImported} onRetry={() => setJobId(null)} />
      ) : (
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            submit.mutate();
          }}
        >
          <div className="flex flex-col gap-1">
            <label htmlFor="import-file" className="font-medium">
              PDF ファイル（20MB まで）
            </label>
            <input
              id="import-file"
              type="file"
              accept="application/pdf,.pdf"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className={inputClass}
            />
            {tooLarge && (
              <p role="alert" className="text-red-600 dark:text-red-400">
                ファイルが大きすぎます（20MB まで）。
              </p>
            )}
          </div>
          <div className="grid gap-3 sm:grid-cols-[10rem_12rem_1fr]">
            <div className="flex flex-col gap-1">
              <label htmlFor="import-label" className="font-medium">
                版ラベル
              </label>
              <input
                id="import-label"
                value={label}
                maxLength={20}
                placeholder="v3.0"
                onChange={(e) => setLabel(e.target.value)}
                className={inputClass}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="import-effective" className="font-medium">
                施行日
              </label>
              <input
                id="import-effective"
                type="date"
                value={effectiveFrom}
                onChange={(e) => setEffectiveFrom(e.target.value)}
                className={inputClass}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="import-note" className="font-medium">
                改定内容（任意）
              </label>
              <input id="import-note" value={changeNote} onChange={(e) => setChangeNote(e.target.value)} className={inputClass} />
            </div>
          </div>
          {submit.error && (
            <p role="alert" className="text-red-600 dark:text-red-400">
              {errorMessage(submit.error)}
            </p>
          )}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={submit.isPending || !file || tooLarge || !label.trim() || !effectiveFrom}
              className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
            >
              {submit.isPending ? "アップロード中…" : "取り込む"}
            </button>
            <button type="button" onClick={onCancel} className="rounded-md border border-border px-4 py-2">
              やめる
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

/** 取り込みの状況。待ち・実行中の間は2秒ごとに確かめる */
function JobStatus({
  jobId,
  onSucceeded,
  onRetry,
}: {
  jobId: string;
  onSucceeded: (versionId: string) => void;
  onRetry: () => void;
}) {
  const { data: job, error } = useQuery({
    queryKey: ["admin", "ingestion-jobs", jobId],
    queryFn: () => apiFetch<IngestionJob>(`/api/v1/admin/ingestion-jobs/${encodeURIComponent(jobId)}`),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "SUCCEEDED" || status === "FAILED" ? false : 2000;
    },
  });
  // 成功したら1回だけ、できた版を開く
  const notified = useRef(false);
  useEffect(() => {
    if (job?.status === "SUCCEEDED" && job.resultVersionId && !notified.current) {
      notified.current = true;
      onSucceeded(job.resultVersionId);
    }
  }, [job, onSucceeded]);

  if (error) return <p role="alert">{errorMessage(error)}</p>;
  if (!job) return <p role="status">取り込みを受け付けました…</p>;
  return (
    <div className="flex flex-col gap-2">
      <p role="status" className={job.status === "FAILED" ? "font-medium text-red-600 dark:text-red-400" : undefined}>
        {job.fileName}（{job.versionLabel}）: {STATUS_LABELS[job.status] ?? job.status}
      </p>
      {job.errorMessage && <p className="text-muted-foreground">{job.errorMessage}</p>}
      {job.status === "FAILED" && (
        <button type="button" onClick={onRetry} className="self-start rounded-md border border-border px-3 py-1">
          別の PDF・版ラベルで取り込み直す
        </button>
      )}
    </div>
  );
}
