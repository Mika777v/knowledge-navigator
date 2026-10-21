import type { Metadata } from "next";
import { LifeEventList } from "./life-event-list";

export const metadata: Metadata = { title: "きっかけの管理 | 社内規程ナビ" };

export default function AdminLifeEventsPage() {
  return <LifeEventList />;
}
