"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { STATUS_LABELS } from "@/lib/content-labels";
import { adminErrorMessage } from "../../admin-errors";
import {
  type AdminProgram,
  adminProgramsKey,
  type ProgramFormValues,
  saveProgram,
  toFormValues,
  transition,
} from "../program-api";
import { ProgramForm } from "../program-form";

/** 制度の編集・公開・公開終了。保存は説明 → 根拠 → 窓口の順に、それぞれ楽観ロック（If-Match）で行う */
export function EditProgram() {
  const { id } = useParams<{ id: string }>();
  const { data, error } = useQuery({
    queryKey: [...adminProgramsKey, id],
    queryFn: () => apiFetch<AdminProgram>(`/api/v1/admin/programs/${encodeURIComponent(id)}`),
  });
  if (error) return <p role="alert">{adminErrorMessage(error)}</p>;
  if (!data) return <p className="text-muted-foreground">読み込み中…</p>;
  return <EditProgramForm key={data.id} program={data} />;
}

function EditProgramForm({ program: loaded }: { program: AdminProgram }) {
  const queryClient = useQueryClient();
  // 最後に保存された状態（版番号・公開状態）。保存のたびに更新し、次の If-Match に使う
  const [program, setProgram] = useState(loaded);
  const [values, setValues] = useState<ProgramFormValues>(() => toFormValues(loaded));
  const [message, setMessage] = useState<string | null>(null);

  const onSaved = (updated: AdminProgram, text: string) => {
    setProgram(updated);
    queryClient.setQueryData([...adminProgramsKey, updated.id], updated);
    void queryClient.invalidateQueries({ queryKey: adminProgramsKey, exact: true });
    setMessage(text);
  };
  const save = useMutation({
    mutationFn: () => saveProgram(program, values),
    onSuccess: (updated) => onSaved(updated, "保存しました。"),
  });
  const change = useMutation({
    mutationFn: async (action: "publish" | "archive") => {
      // 公開するときは、編集中の内容を先に保存してから公開する（保存し忘れた変更が失われないように）
      const current = action === "publish" ? await saveProgram(program, values) : program;
      setProgram(current);
      return transition(current, action);
    },
    onSuccess: (updated, action) => onSaved(updated, action === "publish" ? "公開しました。" : "公開を終了しました。"),
  });
  const error = save.error ?? change.error;
  const busy = save.isPending || change.isPending;

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        setMessage(null);
        change.reset();
        save.mutate();
      }}
    >
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-bold">制度の編集</h1>
        <span className="font-mono text-sm">{program.code}</span>
        <span className="rounded bg-foreground/10 px-2 py-0.5 text-sm">{STATUS_LABELS[program.status]}</span>
        {program.status === "PUBLISHED" && (
          <Link href={`/programs/${program.id}`} className="text-sm underline">
            利用者向けの画面で見る
          </Link>
        )}
      </div>
      <p className="text-sm text-muted-foreground">所管部署: {program.ownerDepartment?.name}</p>
      <ProgramForm values={values} onChange={setValues} />
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
        {program.status !== "PUBLISHED" ? (
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setMessage(null);
              save.reset();
              change.mutate("publish");
            }}
            className="rounded-md border border-border px-4 py-2"
          >
            保存して公開する
          </button>
        ) : (
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              if (!window.confirm("この制度の公開を終了しますか？利用者には表示されなくなります。")) return;
              setMessage(null);
              save.reset();
              change.mutate("archive");
            }}
            className="rounded-md border border-border px-4 py-2"
          >
            公開を終了する
          </button>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        公開するには、根拠の条と窓口がそれぞれ1件以上必要です。公開範囲は、根拠の規程より広くできません。
      </p>
    </form>
  );
}
