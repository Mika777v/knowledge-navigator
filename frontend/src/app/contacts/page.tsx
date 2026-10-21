import type { Metadata } from "next";
import { ContactDirectory } from "./contact-directory";

export const metadata: Metadata = { title: "問い合わせ先の一覧 | 社内規程ナビ" };

export default function ContactsPage() {
  return <ContactDirectory />;
}
