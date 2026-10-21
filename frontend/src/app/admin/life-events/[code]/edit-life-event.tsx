"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { ifMatch } from "@/lib/content-labels";
import {
  type AdminLifeEvent,
  adminLifeEventsKey,
  errorMessage,
  type LifeEventFormValues,
  toFormValues,
  toRequest,
} from "../life-event-api";
import { LifeEventForm } from "../life-event-form";

/** きっかけの編集。紐づける制度もまとめて置き換える（楽観ロック） */
export function EditLifeEvent() {
  const { code } = useParams<{ code: string }>();
  const { data, error } = useQuery({
    queryKey: [...adminLifeEventsKey, code],
    queryFn: () => apiFetch<AdminLifeEvent>(`/api/v1/admin/life-events/${encodeURIComponent(code)}`),
  });
  if (error) return <p role="alert">{errorMessage(error)}</p>;
  if (!data) return <p className="text-muted-foreground">読み込み中…</p>;
  return <EditLifeEventForm key={data.code} lifeEvent={data} />;
}

function EditLifeEventForm({ lifeEvent: loaded }: { lifeEvent: AdminLifeEvent }) {
  const queryClient = useQueryClient();
  const [lifeEvent, setLifeEvent] = useState(loaded);
  const [values, setValues] = useState<LifeEventFormValues>(() => toFormValues(loaded));
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () =>
      apiFetch<AdminLifeEvent>(`/api/v1/admin/life-events/${encodeURIComponent(lifeEvent.code)}`, {
        method: "PUT",
        headers: ifMatch(lifeEvent.rowVersion),
        json: toRequest(values),
      }),
    onSuccess: (updated) => {
      setLifeEvent(updated);
      queryClient.setQueryData([...adminLifeEventsKey, updated.code], updated);
      void queryClient.invalidateQueries({ queryKey: adminLifeEventsKey, exact: true });
      void queryClient.invalidateQueries({ queryKey: ["life-events"] });
      setSaved(true);
    },
  });

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        setSaved(false);
        save.mutate();
      }}
    >
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-bold">きっかけの編集</h1>
        <span className="font-mono text-sm">{lifeEvent.code}</span>
        <Link href={`/search?q=${encodeURIComponent(lifeEvent.name)}`} className="text-sm underline">
          「{lifeEvent.name}」で検索してみる
        </Link>
      </div>
      <LifeEventForm values={values} onChange={setValues} />
      {saved && (
        <p role="status" className="text-sm">
          保存しました。
        </p>
      )}
      {save.error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {errorMessage(save.error)}
        </p>
      )}
      <button
        type="submit"
        disabled={save.isPending}
        className="self-start rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        保存
      </button>
    </form>
  );
}
