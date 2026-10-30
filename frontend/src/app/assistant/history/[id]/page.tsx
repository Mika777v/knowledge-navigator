import type { Metadata } from "next";
import { ConversationView } from "./conversation-view";

export const metadata: Metadata = { title: "AI の回答の履歴 | 社内規程ナビ" };

export default function ConversationPage() {
  return <ConversationView />;
}
