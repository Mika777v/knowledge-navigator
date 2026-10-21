import type { Metadata } from "next";
import { Suspense } from "react";
import { OrganizationAdmin } from "./organization-admin";

export const metadata: Metadata = { title: "組織の管理 | 社内規程ナビ" };

export default function OrganizationPage() {
  // 表示するタブ（?tab=）を読むため Suspense で囲む
  return (
    <Suspense>
      <OrganizationAdmin />
    </Suspense>
  );
}
