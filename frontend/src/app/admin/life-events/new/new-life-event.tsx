"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import {
  type AdminLifeEvent,
  adminLifeEventsKey,
  EMPTY_LIFE_EVENT,
  errorMessage,
  type LifeEventFormValues,
  toRequest,
} from "../life-event-api";
import { LifeEventForm } from "../life-event-form";

/** きっかけを作成する（作成するとすぐにホーム画面の「きっかけから探す」に出る） */
export function NewLifeEvent() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [code, setCode] = useState("");
  const [values, setValues] = useState<LifeEventFormValues>(EMPTY_LIFE_EVENT);

  const create = useMutation({
    mutationFn: () =>
      apiFetch<AdminLifeEvent>("/api/v1/admin/life-events", {
        method: "POST",
        json: { code: code.trim(), content: toRequest(values) },
      }),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: adminLifeEventsKey });
      await queryClient.invalidateQueries({ queryKey: ["life-events"] });
      router.push(`/admin/life-events/${encodeURIComponent(created.code)}`);
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
      <h1 className="text-2xl font-bold">新しいきっかけ</h1>
      <p className="text-sm text-muted-foreground">作成すると、すぐにホーム画面の「きっかけから探す」に表示されます。</p>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="life-event-code" className="font-medium">
          コード（英大文字・数字・ハイフン・下線）
        </label>
        <input
          id="life-event-code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={32}
          placeholder="例: OVERSEAS"
          className="w-56 rounded-md border border-border bg-background px-3 py-2 font-mono"
        />
      </div>
      <LifeEventForm values={values} onChange={setValues} />
      {create.error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {errorMessage(create.error)}
        </p>
      )}
      <button
        type="submit"
        disabled={create.isPending}
        className="self-start rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        作成
      </button>
    </form>
  );
}
