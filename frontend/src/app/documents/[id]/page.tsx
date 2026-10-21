import type { Metadata } from "next";
import { Suspense } from "react";
import { DocumentView } from "./document-view";

export const metadata: Metadata = { title: "規程 | 社内規程ナビ" };

export default function DocumentPage() {
  // URL（文書 ID・版・強調語）はリクエスト時にしか分からないため Suspense で囲む
  return (
    <Suspense>
      <DocumentView />
    </Suspense>
  );
}
