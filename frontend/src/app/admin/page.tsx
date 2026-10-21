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
        <li>
          <Link href="/admin/faqs" className="font-medium underline">
            FAQ
          </Link>
          <p className="text-sm text-muted-foreground">自部署が所管する FAQ を作成・編集・公開します（コンテンツ担当者だけ）。</p>
        </li>
        <li>
          <Link href="/admin/analytics" className="font-medium underline">
            検索の分析
          </Link>
          <p className="text-sm text-muted-foreground">
            何も見つからなかった質問・よく検索される質問・フィードバックを見て、FAQ や辞書の改善につなげます。
          </p>
        </li>
        <li>
          <Link href="/admin/documents" className="font-medium underline">
            規程
          </Link>
          <p className="text-sm text-muted-foreground">
            自部署が所管する規程・文書を、版（本文）ごとに作成・改定・公開します（コンテンツ担当者だけ）。
          </p>
        </li>
        <li>
          <Link href="/admin/audit-logs" className="font-medium underline">
            監査ログ
          </Link>
          <p className="text-sm text-muted-foreground">誰が・いつ・何を変更したかを確認します（システム管理者だけ）。</p>
        </li>
      </ul>
    </div>
  );
}
