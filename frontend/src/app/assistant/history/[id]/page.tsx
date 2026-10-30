import type { Metadata } from "next";
import { Suspense } from "react";
import { ConversationView } from "./conversation-view";

export const metadata: Metadata = { title: "AI の回答の履歴 | 社内規程ナビ" };

export default function ConversationPage() {
  // 会話の ID は URL からしか分からないため Suspense で囲む（ほかの詳細の画面と同じ）
  return (
    <Suspense>
      <ConversationView />
    </Suspense>
  );
}
