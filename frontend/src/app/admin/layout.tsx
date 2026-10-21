import { Suspense } from "react";
import { AdminGuard } from "./admin-guard";
import { AdminNav } from "./admin-nav";

/** 管理画面の共通レイアウト（左にメニュー） */
export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-8 px-4 py-8 md:grid-cols-[12rem_1fr]">
      {/* メニューは現在の URL を見るため Suspense で囲む */}
      <Suspense>
        <AdminNav />
      </Suspense>
      <main className="min-w-0">
        <AdminGuard>{children}</AdminGuard>
      </main>
    </div>
  );
}
