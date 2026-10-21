"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ContactsTab } from "./contacts-tab";
import { DepartmentsTab } from "./departments-tab";
import { UsersTab } from "./users-tab";

const TABS = [
  { key: "departments", label: "部署" },
  { key: "contacts", label: "窓口" },
  { key: "users", label: "ユーザー" },
] as const;

/** 組織マスタ（部署・窓口・ユーザー）の管理（システム管理者だけ）。タブで切り替える */
export function OrganizationAdmin() {
  const tab = useSearchParams().get("tab") ?? "departments";
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">組織</h1>
      <nav aria-label="組織の種類" className="flex gap-2 border-b border-border">
        {TABS.map((t) => (
          <Link
            key={t.key}
            href={`/admin/organization?tab=${t.key}`}
            aria-current={tab === t.key ? "page" : undefined}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${
              tab === t.key ? "border-foreground font-semibold" : "border-transparent text-muted-foreground"
            }`}
          >
            {t.label}
          </Link>
        ))}
      </nav>
      {tab === "contacts" ? <ContactsTab /> : tab === "users" ? <UsersTab /> : <DepartmentsTab />}
    </div>
  );
}
