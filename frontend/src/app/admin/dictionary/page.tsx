import type { Metadata } from "next";
import { Suspense } from "react";
import { DictionaryEditor } from "./dictionary-editor";

export const metadata: Metadata = { title: "キーワード辞書 | 社内規程ナビ" };

export default function DictionaryPage() {
  // ?term= を読むため Suspense で囲む
  return (
    <Suspense>
      <DictionaryEditor />
    </Suspense>
  );
}
