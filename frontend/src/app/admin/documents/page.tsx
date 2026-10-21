import type { Metadata } from "next";
import { DocumentList } from "./document-list";

export const metadata: Metadata = { title: "規程の管理 | 社内規程ナビ" };

export default function AdminDocumentsPage() {
  return <DocumentList />;
}
