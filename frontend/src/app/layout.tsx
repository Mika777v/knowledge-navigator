import type { Metadata } from "next";
import { Suspense } from "react";
import { SiteHeader } from "@/components/site-header";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "社内規程ナビ",
  description:
    "知りたいことから、使える制度・根拠となる規程・問い合わせ先まで案内する社内ナレッジナビゲーション",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ja" className="h-full antialiased">
      <body className="flex min-h-full flex-col">
        <Providers>
          {/* ヘッダーは URL（ログイン画面かどうか）を見るため、動的なページでも先に描画できるよう Suspense で囲む */}
          <Suspense>
            <SiteHeader />
          </Suspense>
          {children}
        </Providers>
      </body>
    </html>
  );
}
