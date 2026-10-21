import Link from "next/link";
import { AdminGuard } from "./admin-guard";

/** 管理画面の共通レイアウト（左にメニュー） */
export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-8 px-4 py-8 md:grid-cols-[12rem_1fr]">
      <nav aria-label="管理メニュー" className="flex flex-col gap-1 text-sm">
        <p className="mb-1 font-semibold">管理</p>
        <Link href="/admin/dictionary" className="rounded px-2 py-1 hover:bg-foreground/5">
          キーワード辞書
        </Link>
      </nav>
      <main className="min-w-0">
        <AdminGuard>{children}</AdminGuard>
      </main>
    </div>
  );
}
