import type { Metadata } from "next";
import { NewProgram } from "./new-program";

export const metadata: Metadata = { title: "新しい制度 | 社内規程ナビ" };

export default function NewProgramPage() {
  return <NewProgram />;
}
