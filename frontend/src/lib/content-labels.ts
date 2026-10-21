import type { ContentStatus, Visibility } from "@/lib/api/types";

export const VISIBILITY_LABELS: Record<Visibility, string> = {
  INTERNAL: "社内公開",
  MANAGERS: "管理職限定",
  RESTRICTED: "部署限定",
  CONFIDENTIAL: "機密",
};

export const STATUS_LABELS: Record<ContentStatus, string> = {
  DRAFT: "下書き",
  PUBLISHED: "公開中",
  ARCHIVED: "公開終了",
};

/** 楽観ロックの If-Match ヘッダー（backend の ETag と同じ形） */
export function ifMatch(rowVersion: number): HeadersInit {
  return { "If-Match": `"${rowVersion}"` };
}
