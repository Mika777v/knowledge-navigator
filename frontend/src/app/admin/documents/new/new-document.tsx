"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { fetchMe, meQueryKey } from "@/lib/auth/api";
import {
  type AdminDocument,
  adminDocumentsKey,
  type DocumentFormValues,
  EMPTY_DOCUMENT,
  errorMessage,
  toRequest,
} from "../document-api";
import { DocumentForm } from "../document-form";

/** 文書を作成する（本文は、作成後に版として登録する）。所管部署はログイン中の担当者の部署になる */
export function NewDocument() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: me } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });
  const [code, setCode] = useState("REG-");
  const [values, setValues] = useState<DocumentFormValues>(EMPTY_DOCUMENT);

  const create = useMutation({
    mutationFn: () =>
      apiFetch<AdminDocument>("/api/v1/admin/documents", {
        method: "POST",
        json: { code: code.trim(), ownerDepartmentCode: me?.department?.code, ...toRequest(values) },
      }),
    onSuccess: async (document) => {
      await queryClient.invalidateQueries({ queryKey: adminDocumentsKey });
      router.push(`/admin/documents/${document.id}`);
    },
  });

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        create.mutate();
      }}
    >
      <h1 className="text-2xl font-bold">新しい文書</h1>
      <p className="text-sm text-muted-foreground">
        所管部署: {me?.department?.name ?? "（所属なし）"}。作成したら、本文を版（例: v1.0）として登録して公開します。
      </p>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="document-code" className="font-medium">
          コード（英大文字・数字・ハイフン）
        </label>
        <input
          id="document-code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={32}
          className="w-56 rounded-md border border-border bg-background px-3 py-2 font-mono"
        />
      </div>
      <DocumentForm values={values} onChange={setValues} />
      {create.error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {errorMessage(create.error)}
        </p>
      )}
      <button
        type="submit"
        disabled={create.isPending || !me?.department}
        className="self-start rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        作成して本文の登録へ
      </button>
    </form>
  );
}
