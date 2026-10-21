import type { Metadata } from "next";
import { FaqList } from "./faq-list";

export const metadata: Metadata = { title: "FAQ の管理 | 社内規程ナビ" };

export default function AdminFaqsPage() {
  return <FaqList />;
}
