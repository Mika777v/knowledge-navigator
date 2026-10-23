import { ApiError, apiFetch } from "@/lib/api/http";
import type { components } from "@/lib/api/schema";
import { ifMatch } from "@/lib/content-labels";

type Schemas = components["schemas"];
export type AdminDocument = Schemas["DocumentResponse"];
export type VersionSummary = Schemas["VersionSummaryResponse"];
export type VersionDetail = Schemas["VersionResponse"];
export type DocumentType = AdminDocument["documentType"];

export const adminDocumentsKey = ["admin", "documents"] as const;

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  REGULATION: "規程",
  RULE: "社内ルール",
  PROCEDURE: "手順書",
  MANUAL: "マニュアル",
  ORG_CHART: "組織図",
};

/** 文書の情報（版をまたいで共通の属性） */
export type DocumentFormValues = {
  title: string;
  summary: string;
  documentType: DocumentType;
  categorySlug: string;
  visibility: AdminDocument["visibility"];
  reviewDueOn: string;
  /** タグの名前（編集のときだけ使う。作成時は付けない） */
  tags: string[];
};

export const EMPTY_DOCUMENT: DocumentFormValues = {
  title: "",
  summary: "",
  documentType: "REGULATION",
  categorySlug: "",
  visibility: "INTERNAL",
  reviewDueOn: "",
  tags: [],
};

export function toFormValues(d: AdminDocument): DocumentFormValues {
  return {
    title: d.title,
    summary: d.summary ?? "",
    documentType: d.documentType,
    categorySlug: d.category?.slug ?? "",
    visibility: d.visibility,
    reviewDueOn: d.reviewDueOn ?? "",
    tags: d.tags ?? [],
  };
}

export function toRequest(v: DocumentFormValues) {
  return {
    title: v.title.trim(),
    summary: v.summary.trim() || undefined,
    documentType: v.documentType,
    categorySlug: v.categorySlug,
    visibility: v.visibility,
    reviewDueOn: v.reviewDueOn || undefined,
  };
}

/** 属性の更新（PUT）。タグもあわせて置き換える */
export function toUpdateRequest(v: DocumentFormValues) {
  return { ...toRequest(v), tags: v.tags };
}

export function documentPath(id: string) {
  return `/api/v1/admin/documents/${encodeURIComponent(id)}`;
}

/** 指定した版を公開する（文書の版番号で楽観ロック） */
export function publishVersion(document: AdminDocument, versionId: string): Promise<AdminDocument> {
  return apiFetch<AdminDocument>(`${documentPath(document.id)}/publish`, {
    method: "POST",
    headers: ifMatch(document.rowVersion),
    json: { versionId },
  });
}

/** 保存・公開のエラーを利用者向けの文にする */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 412) {
    return "ほかの人が先に更新しました。画面を読み込み直してから、もう一度編集してください。";
  }
  return error instanceof ApiError ? error.detail : "処理できませんでした。時間をおいて再度お試しください。";
}
