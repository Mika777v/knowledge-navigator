"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Markdown } from "@/components/markdown";
import { apiFetch } from "@/lib/api/http";
import { articleTitle } from "@/lib/content";
import { PdfImport } from "./pdf-import";
import {
  type AdminDocument,
  adminDocumentsKey,
  adminFileUrl,
  documentPath,
  errorMessage,
  publishVersion,
  type VersionDetail,
  type VersionSummary,
} from "../document-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";

function versionState(v: VersionSummary) {
  if (v.current) return "公開中";
  return v.publishedAt ? "過去の版" : "下書き";
}

/**
 * 版（本文）の管理。公開済みの版は変更できないため、改定は新しい版を作って公開する。
 * 本文は条・見出し単位に自動で分割されるので、保存したら分かれ方を確認できる。
 */
export function VersionsPanel({
  document,
  onPublished,
}: {
  document: AdminDocument;
  onPublished: (document: AdminDocument) => void;
}) {
  const queryClient = useQueryClient();
  const versionsKey = [...adminDocumentsKey, document.id, "versions"];
  const { data: versions } = useQuery({
    queryKey: versionsKey,
    queryFn: () => apiFetch<VersionSummary[]>(`${documentPath(document.id)}/versions`),
  });
  const [selected, setSelected] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [importing, setImporting] = useState(false);
  const shown = selected ?? versions?.find((v) => !v.publishedAt)?.id ?? versions?.[0]?.id ?? null;
  const reloadVersions = () => queryClient.invalidateQueries({ queryKey: versionsKey });

  return (
    <section aria-labelledby="versions-heading" className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h2 id="versions-heading" className="text-lg font-semibold">
          版（本文）
        </h2>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              setImporting(false);
              setCreating(true);
            }}
            className="rounded-md border border-border px-3 py-1 text-sm"
          >
            新しい版を作る
          </button>
          <button
            type="button"
            onClick={() => {
              setCreating(false);
              setImporting(true);
            }}
            className="rounded-md border border-border px-3 py-1 text-sm"
          >
            PDF から版を取り込む
          </button>
        </div>
      </div>
      {importing && (
        <PdfImport
          document={document}
          onCancel={() => setImporting(false)}
          onImported={async (versionId) => {
            await reloadVersions();
            setImporting(false);
            setSelected(versionId);
          }}
        />
      )}
      {versions && versions.length === 0 && !creating && (
        <p className="text-sm">
          まだ版がありません。「新しい版を作る」から本文を登録するか、「PDF から版を取り込む」で規程の PDF を取り込んでください。
        </p>
      )}
      {versions && versions.length > 0 && (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-1 pr-3">版</th>
              <th className="py-1 pr-3">施行日</th>
              <th className="py-1 pr-3">状態</th>
              <th className="py-1 pr-3 text-right">条・見出しの数</th>
              <th className="py-1">
                <span className="sr-only">操作</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id} className="border-b border-border" aria-current={v.id === shown ? "true" : undefined}>
                <td className="py-1 pr-3">{v.versionLabel}</td>
                <td className="py-1 pr-3">{v.effectiveFrom}</td>
                <td className="py-1 pr-3">{versionState(v)}</td>
                <td className="py-1 pr-3 text-right">{v.sectionCount}</td>
                <td className="py-1 text-right">
                  <button
                    type="button"
                    onClick={() => {
                      setCreating(false);
                      setSelected(v.id);
                    }}
                    className="underline"
                  >
                    {v.publishedAt ? `${v.versionLabel} を見る` : `${v.versionLabel} を編集`}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {creating ? (
        <NewVersionForm
          document={document}
          latestVersionId={versions?.[0]?.id}
          onCancel={() => setCreating(false)}
          onCreated={async (version) => {
            await reloadVersions();
            setCreating(false);
            setSelected(version.id);
          }}
        />
      ) : (
        shown && (
          <VersionEditor
            key={shown}
            document={document}
            versionId={shown}
            onSaved={() => void reloadVersions()}
            onPublished={async (updated) => {
              onPublished(updated);
              await reloadVersions();
            }}
          />
        )
      )}
    </section>
  );
}

/**
 * 本文の先頭の front matter（「---」で囲んだ文書の属性）を除く。
 * 以前のダミーデータは front matter ごと本文に保存しているため、新しい版の初期値には含めない
 */
export function withoutFrontMatter(markdown: string): string {
  return markdown.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, "").trimStart();
}

/** 新しい版の作成。改定しやすいよう、最新の版の本文を下書きの初期値にする */
function NewVersionForm({
  document,
  latestVersionId,
  onCancel,
  onCreated,
}: {
  document: AdminDocument;
  latestVersionId: string | undefined;
  onCancel: () => void;
  onCreated: (version: VersionDetail) => void;
}) {
  const { data: latest, isPending } = useQuery({
    queryKey: [...adminDocumentsKey, document.id, "versions", latestVersionId],
    queryFn: () => apiFetch<VersionDetail>(`${documentPath(document.id)}/versions/${latestVersionId}`),
    enabled: !!latestVersionId,
  });
  if (latestVersionId && isPending) return <p className="text-sm text-muted-foreground">読み込み中…</p>;
  return (
    <NewVersionFields
      document={document}
      initialBody={withoutFrontMatter(latest?.bodyMarkdown ?? "")}
      onCancel={onCancel}
      onCreated={onCreated}
    />
  );
}

function NewVersionFields({
  document,
  initialBody,
  onCancel,
  onCreated,
}: {
  document: AdminDocument;
  initialBody: string;
  onCancel: () => void;
  onCreated: (version: VersionDetail) => void;
}) {
  const [label, setLabel] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [changeNote, setChangeNote] = useState("");
  const [body, setBody] = useState(initialBody);
  const create = useMutation({
    mutationFn: () =>
      apiFetch<VersionDetail>(`${documentPath(document.id)}/versions`, {
        method: "POST",
        json: { versionLabel: label.trim(), effectiveFrom, bodyMarkdown: body, changeNote: changeNote.trim() || undefined },
      }),
    onSuccess: onCreated,
  });

  return (
    <form
      className="flex flex-col gap-3 rounded-lg border border-border p-4 text-sm"
      onSubmit={(e) => {
        e.preventDefault();
        create.mutate();
      }}
    >
      <h3 className="font-semibold">新しい版</h3>
      <div className="grid gap-3 sm:grid-cols-[10rem_12rem_1fr]">
        <div className="flex flex-col gap-1">
          <label htmlFor="version-label" className="font-medium">
            版
          </label>
          <input
            id="version-label"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            maxLength={20}
            placeholder="例: v2.2"
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="version-effective" className="font-medium">
            施行日
          </label>
          <input
            id="version-effective"
            type="date"
            value={effectiveFrom}
            onChange={(e) => setEffectiveFrom(e.target.value)}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="version-note" className="font-medium">
            改定内容（任意）
          </label>
          <input id="version-note" value={changeNote} onChange={(e) => setChangeNote(e.target.value)} className={inputClass} />
        </div>
      </div>
      <BodyField value={body} onChange={setBody} />
      {create.error && (
        <p role="alert" className="text-red-600 dark:text-red-400">
          {errorMessage(create.error)}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={create.isPending || !label.trim() || !effectiveFrom || !body.trim()}
          className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
        >
          下書きとして作成
        </button>
        <button type="button" onClick={onCancel} className="rounded-md border border-border px-4 py-2">
          やめる
        </button>
      </div>
    </form>
  );
}

/** 本文（Markdown）。「### 第N条（見出し）」の単位で条に分かれる */
function BodyField({ value, onChange, readOnly = false }: { value: string; onChange?: (v: string) => void; readOnly?: boolean }) {
  const [preview, setPreview] = useState(readOnly);
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between">
        <label htmlFor="version-body" className="font-medium">
          本文（Markdown）
        </label>
        {!readOnly && (
          <button type="button" onClick={() => setPreview(!preview)} className="underline">
            {preview ? "編集に戻る" : "表示を確認"}
          </button>
        )}
      </div>
      {preview ? (
        <div className="max-h-[32rem] overflow-y-auto rounded-md border border-border p-3">
          <Markdown text={value} />
        </div>
      ) : (
        <textarea
          id="version-body"
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          rows={16}
          className={`${inputClass} font-mono text-xs`}
        />
      )}
      {!readOnly && (
        <p className="text-xs text-muted-foreground">
          条は「### 第5条（見出し）」のように書くと、条ごとに分かれて検索・引用できるようになります。
        </p>
      )}
    </div>
  );
}

/** 版の内容。下書きは編集・公開でき、公開済みの版は読むだけ */
function VersionEditor({
  document,
  versionId,
  onSaved,
  onPublished,
}: {
  document: AdminDocument;
  versionId: string;
  onSaved: () => void;
  onPublished: (document: AdminDocument) => void;
}) {
  const path = `${documentPath(document.id)}/versions/${versionId}`;
  const { data, error } = useQuery({
    queryKey: [...adminDocumentsKey, document.id, "versions", versionId],
    queryFn: () => apiFetch<VersionDetail>(path),
  });
  if (error) return <p role="alert">{errorMessage(error)}</p>;
  if (!data) return <p className="text-sm text-muted-foreground">読み込み中…</p>;
  return <VersionFields document={document} version={data} path={path} onSaved={onSaved} onPublished={onPublished} />;
}

function VersionFields({
  document,
  version: loaded,
  path,
  onSaved,
  onPublished,
}: {
  document: AdminDocument;
  version: VersionDetail;
  path: string;
  onSaved: () => void;
  onPublished: (document: AdminDocument) => void;
}) {
  const [version, setVersion] = useState(loaded);
  const [effectiveFrom, setEffectiveFrom] = useState(loaded.effectiveFrom);
  const [changeNote, setChangeNote] = useState(loaded.changeNote ?? "");
  const [body, setBody] = useState(loaded.bodyMarkdown ?? "");
  const [message, setMessage] = useState<string | null>(null);
  const draft = !version.publishedAt;

  const saveDraft = () =>
    apiFetch<VersionDetail>(path, {
      method: "PUT",
      json: { effectiveFrom, bodyMarkdown: body, changeNote: changeNote.trim() || undefined },
    });
  const save = useMutation({
    mutationFn: saveDraft,
    onSuccess: (updated) => {
      setVersion(updated);
      setMessage(`保存しました。本文は ${updated.sections.length} 個の条・見出しに分かれました。`);
      onSaved();
    },
  });
  const publish = useMutation({
    // 編集中の本文を保存してから、この版を公開する
    mutationFn: async () => {
      const saved = await saveDraft();
      setVersion(saved);
      return publishVersion(document, saved.id);
    },
    onSuccess: (updated) => {
      setVersion((v) => ({ ...v, publishedAt: new Date().toISOString() }));
      setMessage(`${version.versionLabel} を公開しました。`);
      onPublished(updated);
    },
  });
  const error = save.error ?? publish.error;
  const busy = save.isPending || publish.isPending;

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border p-4 text-sm">
      <h3 className="font-semibold">
        {version.versionLabel}
        <span className="ml-2 font-normal text-muted-foreground">
          {draft ? "下書き（編集できます）" : "公開済み（変更できません。改定するときは新しい版を作ります）"}
        </span>
      </h3>
      {draft ? (
        <div className="grid gap-3 sm:grid-cols-[12rem_1fr]">
          <div className="flex flex-col gap-1">
            <label htmlFor="edit-effective" className="font-medium">
              施行日
            </label>
            <input
              id="edit-effective"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              className={inputClass}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="edit-note" className="font-medium">
              改定内容（任意）
            </label>
            <input id="edit-note" value={changeNote} onChange={(e) => setChangeNote(e.target.value)} className={inputClass} />
          </div>
        </div>
      ) : (
        <p>
          施行日: {version.effectiveFrom}
          {version.changeNote && `／改定内容: ${version.changeNote}`}
        </p>
      )}
      {version.sourceFileId && (
        <p>
          この版は PDF から取り込みました。{" "}
          <a className="underline" href={adminFileUrl(version.sourceFileId)} target="_blank" rel="noopener">
            原本（PDF）を開く
          </a>
          {draft && "。条の分かれ方と原本のページを確かめ、必要なら本文を直してから公開してください。"}
        </p>
      )}
      <BodyField value={body} onChange={setBody} readOnly={!draft} />

      <details className="rounded-md border border-border p-3" open={!draft || !!version.sourceFileId}>
        <summary className="cursor-pointer font-medium">条・見出しの分かれ方（{version.sections.length}）</summary>
        <ol className="mt-2 grid gap-1 sm:grid-cols-2">
          {version.sections.map((s) => (
            <li key={s.id}>
              {articleTitle(s.articleNo, s.heading) || "（見出しなし）"}
              {version.sourceFileId && s.pageFrom != null && (
                <a
                  className="ml-2 text-xs text-muted-foreground underline"
                  href={adminFileUrl(version.sourceFileId, s.pageFrom)}
                  target="_blank"
                  rel="noopener"
                >
                  p.{s.pageFrom}
                  {s.pageTo != null && s.pageTo !== s.pageFrom ? `–${s.pageTo}` : ""}
                </a>
              )}
            </li>
          ))}
        </ol>
      </details>

      {message && <p role="status">{message}</p>}
      {error && (
        <p role="alert" className="text-red-600 dark:text-red-400">
          {errorMessage(error)}
        </p>
      )}
      {draft && (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setMessage(null);
              publish.reset();
              save.mutate();
            }}
            className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
          >
            下書きを保存
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              if (!window.confirm(`${version.versionLabel} を公開しますか？公開した版は変更できなくなります。`)) return;
              setMessage(null);
              save.reset();
              publish.mutate();
            }}
            className="rounded-md border border-border px-4 py-2"
          >
            保存してこの版を公開する
          </button>
        </div>
      )}
    </div>
  );
}
