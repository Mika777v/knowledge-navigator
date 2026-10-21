import type { Metadata } from "next";
import { Suspense } from "react";
import { EditFaq } from "./edit-faq";

export const metadata: Metadata = { title: "FAQ の編集 | 社内規程ナビ" };

export default function EditFaqPage() {
  return (
    <Suspense>
      <EditFaq />
    </Suspense>
  );
}
