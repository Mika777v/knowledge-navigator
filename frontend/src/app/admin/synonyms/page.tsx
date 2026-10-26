import type { Metadata } from "next";
import { SynonymEditor } from "./synonym-editor";

export const metadata: Metadata = { title: "類義語辞書 | 社内規程ナビ" };

export default function SynonymsPage() {
  return <SynonymEditor />;
}
