import type { Metadata } from "next";
import { Suspense } from "react";
import { EditDocument } from "./edit-document";

export const metadata: Metadata = { title: "文書の編集 | 社内規程ナビ" };

export default function EditDocumentPage() {
  return (
    <Suspense>
      <EditDocument />
    </Suspense>
  );
}
