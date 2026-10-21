import type { Metadata } from "next";
import { Suspense } from "react";
import { ProgramView } from "./program-view";

export const metadata: Metadata = { title: "制度 | 社内規程ナビ" };

export default function ProgramPage() {
  return (
    <Suspense>
      <ProgramView />
    </Suspense>
  );
}
