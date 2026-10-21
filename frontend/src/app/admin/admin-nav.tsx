"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { fetchMe, meQueryKey, type Me } from "@/lib/auth/api";
import { cn } from "@/lib/utils";

type MenuItem = { href: string; label: string; roles: Me["role"][] };

/** 管理メニュー。各画面の API と同じロールにだけ表示する */
const MENU: MenuItem[] = [
  { href: "/admin/dictionary", label: "キーワード辞書", roles: ["CONTENT_EDITOR", "SYSTEM_ADMIN"] },
  // FAQ の編集は所管部署のコンテンツ担当者だけ（システム管理者は職務分離のため編集できない）
  { href: "/admin/faqs", label: "FAQ", roles: ["CONTENT_EDITOR"] },
  { href: "/admin/analytics", label: "検索の分析", roles: ["CONTENT_EDITOR", "SYSTEM_ADMIN"] },
  { href: "/admin/documents", label: "規程", roles: ["CONTENT_EDITOR"] },
  { href: "/admin/audit-logs", label: "監査ログ", roles: ["SYSTEM_ADMIN"] },
];

export function AdminNav() {
  const pathname = usePathname();
  const { data: me } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });
  const items = MENU.filter((item) => me && item.roles.includes(me.role));
  return (
    <nav aria-label="管理メニュー" className="flex flex-col gap-1 text-sm">
      <p className="mb-1 font-semibold">管理</p>
      {items.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          aria-current={pathname.startsWith(item.href) ? "page" : undefined}
          className={cn(
            "rounded px-2 py-1 hover:bg-foreground/5",
            pathname.startsWith(item.href) && "bg-foreground/10 font-medium",
          )}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
