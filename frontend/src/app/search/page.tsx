import type { Metadata } from "next";
import { Suspense } from "react";
import { SearchResults } from "./search-results";

export const metadata: Metadata = { title: "検索結果 | 社内規程ナビ" };

export default function SearchPage() {
  // 検索語（?q=）はリクエスト時にしか分からないため Suspense で囲む
  return (
    <Suspense>
      <SearchResults />
    </Suspense>
  );
}
