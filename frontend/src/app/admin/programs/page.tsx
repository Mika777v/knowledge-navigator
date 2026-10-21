import type { Metadata } from "next";
import { ProgramList } from "./program-list";

export const metadata: Metadata = { title: "制度の管理 | 社内規程ナビ" };

export default function AdminProgramsPage() {
  return <ProgramList />;
}
