import type { Metadata } from "next";
import { Suspense } from "react";
import { DocumentList } from "./document-list";

export const metadata: Metadata = { title: "規程の一覧 | 社内規程ナビ" };

export default function DocumentsPage() {
  // useSearchParams を使うため Suspense で囲む
  return (
    <Suspense>
      <DocumentList />
    </Suspense>
  );
}
