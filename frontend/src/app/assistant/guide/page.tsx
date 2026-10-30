import type { Metadata } from "next";
import { Suspense } from "react";
import { GuideView } from "./guide-view";

export const metadata: Metadata = { title: "手続きの案内 | 社内規程ナビ" };

export default function GuidePage() {
  // きっかけ（?event=）はリクエスト時にしか分からないため Suspense で囲む
  return (
    <Suspense>
      <GuideView />
    </Suspense>
  );
}
