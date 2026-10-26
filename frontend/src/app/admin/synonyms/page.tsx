import type { Metadata } from "next";
import { Suspense } from "react";
import { SynonymEditor } from "./synonym-editor";

export const metadata: Metadata = { title: "類義語辞書 | 社内規程ナビ" };

export default function SynonymsPage() {
  // ?term= を読むため Suspense で囲む
  return (
    <Suspense>
      <SynonymEditor />
    </Suspense>
  );
}
