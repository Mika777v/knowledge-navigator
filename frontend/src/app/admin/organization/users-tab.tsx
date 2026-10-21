"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { ifMatch } from "@/lib/content-labels";
import {
  type Department,
  EMPLOYMENT_OPTIONS,
  errorMessage,
  organizationKey,
  ROLE_OPTIONS,
  type User,
} from "./organization-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";
const usersKey = [...organizationKey, "users"];

type Values = {
  email: string;
  initialPassword: string;
  displayName: string;
  departmentCode: string;
  role: string;
  employmentType: string;
  joinedOn: string;
  active: boolean;
};

function toValues(u?: User): Values {
  return {
    email: u?.email ?? "",
    initialPassword: "",
    displayName: u?.displayName ?? "",
    departmentCode: u?.departmentCode ?? "",
    role: u?.role ?? "EMPLOYEE",
    employmentType: u?.employmentType ?? "REGULAR",
    joinedOn: u?.joinedOn ?? "",
    active: u?.active ?? true,
  };
}

/**
 * ユーザーの一覧と編集（パスワードは表示しない）。
 * 自分自身のロール変更・無効化と、最後のシステム管理者を外すことは backend が断る
 */
export function UsersTab() {
  const users = useQuery({ queryKey: usersKey, queryFn: () => apiFetch<User[]>("/api/v1/admin/users") });
  const departments = useQuery({
    queryKey: [...organizationKey, "departments"],
    queryFn: () => apiFetch<Department[]>("/api/v1/admin/departments"),
  });
  const [editing, setEditing] = useState<User | "new" | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const deptNames = new Map(departments.data?.map((d) => [d.code, d.name]));

  if (users.error) return <p role="alert">{errorMessage(users.error)}</p>;
  if (!users.data || !departments.data) return <p className="text-muted-foreground">読み込み中…</p>;
  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex items-center justify-between">
        <p className="text-muted-foreground">ロールで、見られる内容と使える管理機能が決まります。</p>
        <button
          type="button"
          onClick={() => {
            setMessage(null);
            setEditing("new");
          }}
          className="rounded-md border border-border px-3 py-1"
        >
          ユーザーを追加
        </button>
      </div>
      {message && <p role="status">{message}</p>}
      {editing && (
        <UserEditor
          key={editing === "new" ? "new" : editing.id}
          user={editing === "new" ? undefined : editing}
          departments={departments.data}
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
            <th className="py-1 pr-3">名前</th>
            <th className="py-1 pr-3">メールアドレス</th>
            <th className="py-1 pr-3">所属</th>
            <th className="py-1 pr-3">ロール</th>
            <th className="py-1 pr-3">状態</th>
            <th className="py-1">
              <span className="sr-only">操作</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {users.data.map((u) => (
            <tr key={u.id} className="border-b border-border">
              <td className="py-1 pr-3">{u.displayName}</td>
              <td className="py-1 pr-3">{u.email}</td>
              <td className="py-1 pr-3">{u.departmentCode ? deptNames.get(u.departmentCode) : "—"}</td>
              <td className="py-1 pr-3">{ROLE_OPTIONS[u.role as keyof typeof ROLE_OPTIONS] ?? u.role}</td>
              <td className="py-1 pr-3">{u.active ? "有効" : "無効"}</td>
              <td className="py-1 text-right">
                <button
                  type="button"
                  onClick={() => {
                    setMessage(null);
                    setEditing(u);
                  }}
                  className="underline"
                >
                  {u.displayName}を編集
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function UserEditor({
  user,
  departments,
  onDone,
  onCancel,
}: {
  user: User | undefined;
  departments: Department[];
  onDone: (message: string) => void;
  onCancel: () => void;
}) {
  const queryClient = useQueryClient();
  const [v, setV] = useState<Values>(() => toValues(user));
  const content = {
    displayName: v.displayName.trim(),
    departmentCode: v.departmentCode || undefined,
    role: v.role,
    employmentType: v.employmentType,
    joinedOn: v.joinedOn || undefined,
    active: v.active,
  };
  const save = useMutation({
    mutationFn: () =>
      user
        ? apiFetch<User>(`/api/v1/admin/users/${encodeURIComponent(user.id)}`, {
            method: "PUT",
            headers: ifMatch(user.rowVersion),
            json: content,
          })
        : apiFetch<User>("/api/v1/admin/users", {
            method: "POST",
            json: { email: v.email.trim(), initialPassword: v.initialPassword, content },
          }),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: usersKey });
      onDone(`${saved.displayName}さんを保存しました。`);
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
      <h2 className="font-semibold">{user ? `${user.displayName}さんの編集` : "ユーザーを追加"}</h2>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="user-email" className="font-medium">
            メールアドレス
          </label>
          <input
            id="user-email"
            type="email"
            value={v.email}
            disabled={!!user}
            onChange={(e) => setV({ ...v, email: e.target.value })}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="user-name" className="font-medium">
            表示名
          </label>
          <input
            id="user-name"
            value={v.displayName}
            onChange={(e) => setV({ ...v, displayName: e.target.value })}
            className={inputClass}
          />
        </div>
        {!user && (
          <div className="flex flex-col gap-1">
            <label htmlFor="user-password" className="font-medium">
              初期パスワード（8文字以上）
            </label>
            <input
              id="user-password"
              type="password"
              autoComplete="new-password"
              value={v.initialPassword}
              onChange={(e) => setV({ ...v, initialPassword: e.target.value })}
              className={inputClass}
            />
          </div>
        )}
      </div>
      <div className="grid gap-3 sm:grid-cols-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="user-department" className="font-medium">
            所属
          </label>
          <select
            id="user-department"
            value={v.departmentCode}
            onChange={(e) => setV({ ...v, departmentCode: e.target.value })}
            className={inputClass}
          >
            <option value="">所属なし</option>
            {departments.map((d) => (
              <option key={d.code} value={d.code}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="user-role" className="font-medium">
            ロール
          </label>
          <select id="user-role" value={v.role} onChange={(e) => setV({ ...v, role: e.target.value })} className={inputClass}>
            {Object.entries(ROLE_OPTIONS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="user-employment" className="font-medium">
            雇用形態
          </label>
          <select
            id="user-employment"
            value={v.employmentType}
            onChange={(e) => setV({ ...v, employmentType: e.target.value })}
            className={inputClass}
          >
            {Object.entries(EMPLOYMENT_OPTIONS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="user-joined" className="font-medium">
            入社日（任意）
          </label>
          <input
            id="user-joined"
            type="date"
            value={v.joinedOn}
            onChange={(e) => setV({ ...v, joinedOn: e.target.value })}
            className={inputClass}
          />
        </div>
      </div>
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={v.active} onChange={(e) => setV({ ...v, active: e.target.checked })} />
        有効（無効にするとログインできなくなります）
      </label>
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
