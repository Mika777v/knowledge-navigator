import type { Metadata } from "next";
import { Suspense } from "react";
import { FaqList } from "./faq-list";

export const metadata: Metadata = { title: "よくある質問 | 社内規程ナビ" };

export default function FaqsPage() {
  // useSearchParams を使うため Suspense で囲む
  return (
    <Suspense>
      <FaqList />
    </Suspense>
  );
}
