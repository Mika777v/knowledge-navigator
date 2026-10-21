"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { ifMatch } from "@/lib/content-labels";
import { type Department, errorMessage, organizationKey } from "./organization-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";
const departmentsKey = [...organizationKey, "departments"];

type Values = { code: string; name: string; description: string; parentCode: string; sortOrder: string; active: boolean };

function toValues(d?: Department): Values {
  return {
    code: d?.code ?? "",
    name: d?.name ?? "",
    description: d?.description ?? "",
    parentCode: d?.parentCode ?? "",
    sortOrder: String(d?.sortOrder ?? 0),
    active: d?.active ?? true,
  };
}

/** 部署の一覧と編集。上位の部署を選んで階層を作る */
export function DepartmentsTab() {
  const { data, error } = useQuery({
    queryKey: departmentsKey,
    queryFn: () => apiFetch<Department[]>("/api/v1/admin/departments"),
  });
  const [editing, setEditing] = useState<Department | "new" | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const names = new Map(data?.map((d) => [d.code, d.name]));

  if (error) return <p role="alert">{errorMessage(error)}</p>;
  if (!data) return <p className="text-muted-foreground">読み込み中…</p>;
  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex items-center justify-between">
        <p className="text-muted-foreground">文書・制度の所管と、編集権限の単位になります。</p>
        <button
          type="button"
          onClick={() => {
            setMessage(null);
            setEditing("new");
          }}
          className="rounded-md border border-border px-3 py-1"
        >
          部署を追加
        </button>
      </div>
      {message && <p role="status">{message}</p>}
      {editing && (
        <DepartmentEditor
          key={editing === "new" ? "new" : editing.code}
          department={editing === "new" ? undefined : editing}
          departments={data}
          onDone={(text) => {
            setEditing(null);
            setMessage(text);
          }}
          onCancel={() => setEditing(null)}
        />
      )}
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-border">
            <th className="py-1 pr-3">コード</th>
            <th className="py-1 pr-3">名前</th>
            <th className="py-1 pr-3">上位の部署</th>
            <th className="py-1 pr-3">状態</th>
            <th className="py-1">
              <span className="sr-only">操作</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.code} className="border-b border-border">
              <td className="py-1 pr-3 font-mono text-xs">{d.code}</td>
              <td className="py-1 pr-3">{d.name}</td>
              <td className="py-1 pr-3">{d.parentCode ? names.get(d.parentCode) : "—"}</td>
              <td className="py-1 pr-3">{d.active ? "有効" : "無効"}</td>
              <td className="py-1 text-right">
                <button
                  type="button"
                  onClick={() => {
                    setMessage(null);
                    setEditing(d);
                  }}
                  className="underline"
                >
                  {d.name}を編集
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DepartmentEditor({
  department,
  departments,
  onDone,
  onCancel,
}: {
  department: Department | undefined;
  departments: Department[];
  onDone: (message: string) => void;
  onCancel: () => void;
}) {
  const queryClient = useQueryClient();
  const [v, setV] = useState<Values>(() => toValues(department));
  const content = {
    name: v.name.trim(),
    description: v.description.trim() || undefined,
    parentCode: v.parentCode || undefined,
    sortOrder: Number(v.sortOrder) || 0,
    active: v.active,
  };
  const save = useMutation({
    mutationFn: () =>
      department
        ? apiFetch<Department>(`/api/v1/admin/departments/${encodeURIComponent(department.code)}`, {
            method: "PUT",
            headers: ifMatch(department.rowVersion),
            json: content,
          })
        : apiFetch<Department>("/api/v1/admin/departments", {
            method: "POST",
            json: { code: v.code.trim(), content },
          }),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: departmentsKey });
      onDone(`${saved.name}を保存しました。`);
    },
  });

  return (
    <form
      className="flex flex-col gap-3 rounded-lg border border-border p-4"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <h2 className="font-semibold">{department ? `${department.name}の編集` : "部署を追加"}</h2>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="dept-code" className="font-medium">
            コード
          </label>
          <input
            id="dept-code"
            value={v.code}
            disabled={!!department}
            onChange={(e) => setV({ ...v, code: e.target.value.toUpperCase() })}
            className={`${inputClass} font-mono`}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="dept-name" className="font-medium">
            名前
          </label>
          <input id="dept-name" value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} className={inputClass} />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="dept-parent" className="font-medium">
            上位の部署
          </label>
          <select
            id="dept-parent"
            value={v.parentCode}
            onChange={(e) => setV({ ...v, parentCode: e.target.value })}
            className={inputClass}
          >
            <option value="">なし（最上位）</option>
            {departments
              .filter((d) => d.code !== department?.code)
              .map((d) => (
                <option key={d.code} value={d.code}>
                  {d.name}（{d.code}）
                </option>
              ))}
          </select>
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-[1fr_8rem_auto]">
        <div className="flex flex-col gap-1">
          <label htmlFor="dept-description" className="font-medium">
            説明（任意）
          </label>
          <input
            id="dept-description"
            value={v.description}
            onChange={(e) => setV({ ...v, description: e.target.value })}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="dept-sort" className="font-medium">
            表示順
          </label>
          <input
            id="dept-sort"
            type="number"
            min={0}
            value={v.sortOrder}
            onChange={(e) => setV({ ...v, sortOrder: e.target.value })}
            className={inputClass}
          />
        </div>
        <label className="flex items-center gap-2 self-end pb-2">
          <input type="checkbox" checked={v.active} onChange={(e) => setV({ ...v, active: e.target.checked })} />
          有効
        </label>
      </div>
      {save.error && (
        <p role="alert" className="text-red-600 dark:text-red-400">
          {errorMessage(save.error)}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={save.isPending}
          className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
        >
          保存
        </button>
        <button type="button" onClick={onCancel} className="rounded-md border border-border px-4 py-2">
          やめる
        </button>
      </div>
    </form>
  );
}
