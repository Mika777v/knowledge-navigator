import type { Metadata } from "next";
import { Suspense } from "react";
import { FaqView } from "./faq-view";

export const metadata: Metadata = { title: "よくある質問 | 社内規程ナビ" };

export default function FaqPage() {
  return (
    <Suspense>
      <FaqView />
    </Suspense>
  );
}
