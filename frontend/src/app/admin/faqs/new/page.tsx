import type { Metadata } from "next";
import { NewFaq } from "./new-faq";

export const metadata: Metadata = { title: "新しい FAQ | 社内規程ナビ" };

export default function NewFaqPage() {
  return <NewFaq />;
}
