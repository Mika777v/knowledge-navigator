import type { Metadata } from "next";
import { Suspense } from "react";
import { NewFaq } from "./new-faq";

export const metadata: Metadata = { title: "新しい FAQ | 社内規程ナビ" };

export default function NewFaqPage() {
  // ?question= を読むため Suspense で囲む
  return (
    <Suspense>
      <NewFaq />
    </Suspense>
  );
}
