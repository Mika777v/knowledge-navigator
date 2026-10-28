import type { Metadata } from "next";
import { Suspense } from "react";
import { AssistantView } from "./assistant-view";

export const metadata: Metadata = { title: "AI の回答 | 社内規程ナビ" };

export default function AssistantPage() {
  // 質問（?q=）はリクエスト時にしか分からないため Suspense で囲む
  return (
    <Suspense>
      <AssistantView />
    </Suspense>
  );
}
