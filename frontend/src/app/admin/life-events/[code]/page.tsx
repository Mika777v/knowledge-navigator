import type { Metadata } from "next";
import { Suspense } from "react";
import { EditLifeEvent } from "./edit-life-event";

export const metadata: Metadata = { title: "きっかけの編集 | 社内規程ナビ" };

export default function EditLifeEventPage() {
  return (
    <Suspense>
      <EditLifeEvent />
    </Suspense>
  );
}
