import type { Metadata } from "next";
import { HistoryList } from "./history-list";

export const metadata: Metadata = { title: "AI の回答の履歴 | 社内規程ナビ" };

export default function HistoryPage() {
  return <HistoryList />;
}
