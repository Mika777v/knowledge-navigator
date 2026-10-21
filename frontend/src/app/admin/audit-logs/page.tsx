import type { Metadata } from "next";
import { AuditLogViewer } from "./audit-log-viewer";

export const metadata: Metadata = { title: "監査ログ | 社内規程ナビ" };

export default function AuditLogsPage() {
  return <AuditLogViewer />;
}
