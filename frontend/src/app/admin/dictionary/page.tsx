import type { Metadata } from "next";
import { DictionaryEditor } from "./dictionary-editor";

export const metadata: Metadata = { title: "キーワード辞書 | 社内規程ナビ" };

export default function DictionaryPage() {
  return <DictionaryEditor />;
}
