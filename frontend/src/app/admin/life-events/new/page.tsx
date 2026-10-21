import type { Metadata } from "next";
import { NewLifeEvent } from "./new-life-event";

export const metadata: Metadata = { title: "新しいきっかけ | 社内規程ナビ" };

export default function NewLifeEventPage() {
  return <NewLifeEvent />;
}
