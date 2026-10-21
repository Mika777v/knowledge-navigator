"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { fetchMe, meQueryKey } from "@/lib/auth/api";
import { adminErrorMessage } from "../../admin-errors";
import {
  type AdminProgram,
  adminProgramsKey,
  EMPTY_PROGRAM,
  type ProgramFormValues,
  saveRelations,
  toAttributes,
} from "../program-api";
import { ProgramForm } from "../program-form";

/** 制度を下書きとして作成する。所管部署はログイン中の担当者の部署になる */
export function NewProgram() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: me } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });
  const [code, setCode] = useState("PRG-");
  const [values, setValues] = useState<ProgramFormValues>(EMPTY_PROGRAM);

  const create = useMutation({
    mutationFn: async () => {
      const created = await apiFetch<AdminProgram>("/api/v1/admin/programs", {
        method: "POST",
        json: { code: code.trim(), ownerDepartmentCode: me?.department?.code, attributes: toAttributes(values) },
      });
      // 根拠・窓口は別の API で登録する（入力がなければ作成だけで終わる）
      const hasRelations = values.basis.some((b) => b.documentCode) || values.contacts.some((c) => c.contactCode);
      return hasRelations ? saveRelations(created, values) : created;
    },
    onSuccess: async (program) => {
      await queryClient.invalidateQueries({ queryKey: adminProgramsKey });
      router.push(`/admin/programs/${program.id}`);
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
      <h1 className="text-2xl font-bold">新しい制度</h1>
      <p className="text-sm text-muted-foreground">
        下書きとして保存します。所管部署: {me?.department?.name ?? "（所属なし）"}。公開は保存したあとに行います。
      </p>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="program-code" className="font-medium">
          コード（英大文字・数字・ハイフン）
        </label>
        <input
          id="program-code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={32}
          className="w-56 rounded-md border border-border bg-background px-3 py-2 font-mono"
        />
      </div>
      <ProgramForm values={values} onChange={setValues} />
      {create.error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {adminErrorMessage(create.error)}
        </p>
      )}
      <button
        type="submit"
        disabled={create.isPending || !me?.department}
        className="self-start rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        下書きとして保存
      </button>
    </form>
  );
}
