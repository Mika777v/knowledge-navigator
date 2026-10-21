import type { Metadata } from "next";
import { Suspense } from "react";
import { EditProgram } from "./edit-program";

export const metadata: Metadata = { title: "制度の編集 | 社内規程ナビ" };

export default function EditProgramPage() {
  return (
    <Suspense>
      <EditProgram />
    </Suspense>
  );
}
