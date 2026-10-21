import { ApiError } from "@/lib/api/http";
import type { components } from "@/lib/api/schema";

type Schemas = components["schemas"];
export type Department = Schemas["OrgDepartmentView"];
export type Contact = Schemas["OrgContactView"];
export type User = Schemas["OrgUserView"];

export const organizationKey = ["admin", "organization"] as const;

export const ROLE_OPTIONS = {
  EMPLOYEE: "一般社員",
  MANAGER: "管理職",
  CONTENT_EDITOR: "コンテンツ担当者",
  SYSTEM_ADMIN: "システム管理者",
} as const;

export const EMPLOYMENT_OPTIONS = {
  REGULAR: "正社員",
  CONTRACT: "契約社員",
  PART_TIME: "パートタイム",
} as const;

/** 保存のエラーを利用者向けの文にする */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 412) {
    return "ほかの人が先に更新しました。画面を読み込み直してから、もう一度編集してください。";
  }
  return error instanceof ApiError ? error.detail : "処理できませんでした。時間をおいて再度お試しください。";
}
