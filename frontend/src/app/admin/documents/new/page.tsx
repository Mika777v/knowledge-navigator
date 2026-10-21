import type { Metadata } from "next";
import { NewDocument } from "./new-document";

export const metadata: Metadata = { title: "新しい文書 | 社内規程ナビ" };

export default function NewDocumentPage() {
  return <NewDocument />;
}
