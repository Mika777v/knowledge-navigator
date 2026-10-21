"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import { ifMatch } from "@/lib/content-labels";
import { type Contact, type Department, errorMessage, organizationKey } from "./organization-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";
const contactsKey = [...organizationKey, "contacts"];

type Values = Omit<Contact, "id" | "rowVersion" | "sortOrder"> & { sortOrder: string };

function toValues(c?: Contact): Values {
  return {
    code: c?.code ?? "",
    departmentCode: c?.departmentCode ?? "",
    name: c?.name ?? "",
    description: c?.description ?? "",
    email: c?.email ?? "",
    phoneExt: c?.phoneExt ?? "",
    chatUrl: c?.chatUrl ?? "",
    officeHours: c?.officeHours ?? "",
    primary: c?.primary ?? false,
    sortOrder: String(c?.sortOrder ?? 0),
  };
}

/**
 * 問い合わせ窓口の一覧と編集。検索結果の「問い合わせ先」に出る内容。
 * 部署の代表窓口は1つだけで、窓口が見つからないときの案内先になる
 */
export function ContactsTab() {
  const contacts = useQuery({
    queryKey: contactsKey,
    queryFn: () => apiFetch<Contact[]>("/api/v1/admin/contacts"),
  });
  const departments = useQuery({
    queryKey: [...organizationKey, "departments"],
    queryFn: () => apiFetch<Department[]>("/api/v1/admin/departments"),
  });
  const [editing, setEditing] = useState<Contact | "new" | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const deptNames = new Map(departments.data?.map((d) => [d.code, d.name]));

  if (contacts.error) return <p role="alert">{errorMessage(contacts.error)}</p>;
  if (!contacts.data || !departments.data) return <p className="text-muted-foreground">読み込み中…</p>;
  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex items-center justify-between">
        <p className="text-muted-foreground">検索結果の「問い合わせ先」に表示されます。</p>
        <button
          type="button"
          onClick={() => {
            setMessage(null);
            setEditing("new");
          }}
          className="rounded-md border border-border px-3 py-1"
        >
          窓口を追加
        </button>
      </div>
      {message && <p role="status">{message}</p>}
      {editing && (
        <ContactEditor
          key={editing === "new" ? "new" : editing.code}
          contact={editing === "new" ? undefined : editing}
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
            <th className="py-1 pr-3">部署</th>
            <th className="py-1 pr-3">連絡先</th>
            <th className="py-1">
              <span className="sr-only">操作</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {contacts.data.map((c) => (
            <tr key={c.code} className="border-b border-border align-top">
              <td className="py-1 pr-3">
                {c.name}
                {c.primary && <span className="ml-2 rounded bg-foreground/10 px-1.5 text-xs">代表窓口</span>}
              </td>
              <td className="py-1 pr-3">{deptNames.get(c.departmentCode) ?? c.departmentCode}</td>
              <td className="py-1 pr-3">{[c.phoneExt && `内線 ${c.phoneExt}`, c.email].filter(Boolean).join("・")}</td>
              <td className="py-1 text-right">
                <button
                  type="button"
                  onClick={() => {
                    setMessage(null);
                    setEditing(c);
                  }}
                  className="underline"
                >
                  {c.name}を編集
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ContactEditor({
  contact,
  departments,
  onDone,
  onCancel,
}: {
  contact: Contact | undefined;
  departments: Department[];
  onDone: (message: string) => void;
  onCancel: () => void;
}) {
  const queryClient = useQueryClient();
  const [v, setV] = useState<Values>(() => toValues(contact));
  const optional = (s: string) => s.trim() || undefined;
  const content = {
    departmentCode: v.departmentCode,
    name: v.name.trim(),
    description: optional(v.description),
    email: optional(v.email),
    phoneExt: optional(v.phoneExt),
    chatUrl: optional(v.chatUrl),
    officeHours: optional(v.officeHours),
    primary: v.primary,
    sortOrder: Number(v.sortOrder) || 0,
  };
  const save = useMutation({
    mutationFn: () =>
      contact
        ? apiFetch<Contact>(`/api/v1/admin/contacts/${encodeURIComponent(contact.code)}`, {
            method: "PUT",
            headers: ifMatch(contact.rowVersion),
            json: content,
          })
        : apiFetch<Contact>("/api/v1/admin/contacts", { method: "POST", json: { code: v.code.trim(), content } }),
    onSuccess: async (saved) => {
      // 代表窓口を付け替えると、同じ部署の前の代表窓口も変わるので一覧ごと読み直す
      await queryClient.invalidateQueries({ queryKey: contactsKey });
      onDone(`${saved.name}を保存しました。`);
    },
  });
  const text = (key: keyof Values, label: string, type = "text") => (
    <div className="flex flex-col gap-1">
      <label htmlFor={`contact-${key}`} className="font-medium">
        {label}
      </label>
      <input
        id={`contact-${key}`}
        type={type}
        value={v[key] as string}
        disabled={key === "code" && !!contact}
        onChange={(e) => setV({ ...v, [key]: key === "code" ? e.target.value.toUpperCase() : e.target.value })}
        className={inputClass}
      />
    </div>
  );

  return (
    <form
      className="flex flex-col gap-3 rounded-lg border border-border p-4"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <h2 className="font-semibold">{contact ? `${contact.name}の編集` : "窓口を追加"}</h2>
      <div className="grid gap-3 sm:grid-cols-3">
        {text("code", "コード")}
        {text("name", "名前")}
        <div className="flex flex-col gap-1">
          <label htmlFor="contact-department" className="font-medium">
            部署
          </label>
          <select
            id="contact-department"
            value={v.departmentCode}
            onChange={(e) => setV({ ...v, departmentCode: e.target.value })}
            className={inputClass}
          >
            <option value="">選んでください</option>
            {departments.map((d) => (
              <option key={d.code} value={d.code}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
      </div>
      {text("description", "説明（任意）")}
      <fieldset className="grid gap-3 rounded-md border border-border p-3 sm:grid-cols-3">
        <legend className="px-1 font-medium">連絡手段（1つ以上）</legend>
        {text("phoneExt", "内線")}
        {text("email", "メール", "email")}
        {text("chatUrl", "チャットの URL（https://）", "url")}
      </fieldset>
      <div className="grid gap-3 sm:grid-cols-[1fr_8rem_auto]">
        {text("officeHours", "受付時間（任意）")}
        {text("sortOrder", "表示順", "number")}
        <label className="flex items-center gap-2 self-end pb-2">
          <input type="checkbox" checked={v.primary} onChange={(e) => setV({ ...v, primary: e.target.checked })} />
          部署の代表窓口
        </label>
      </div>
      {v.primary && !contact?.primary && (
        <p className="text-xs text-muted-foreground">代表窓口にすると、同じ部署の今の代表窓口は外れます。</p>
      )}
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
