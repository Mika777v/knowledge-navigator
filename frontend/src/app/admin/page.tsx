import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "管理 | 社内規程ナビ" };

export default function AdminHomePage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">管理</h1>
      <ul className="flex flex-col gap-2">
        <li>
          <Link href="/admin/dictionary" className="font-medium underline">
            キーワード辞書
          </Link>
          <p className="text-sm text-muted-foreground">
            利用者の言葉（例: 「入籍」）を、制度・きっかけ・窓口などに直接案内する辞書を確認・編集します。
          </p>
        </li>
      </ul>
    </div>
  );
}
