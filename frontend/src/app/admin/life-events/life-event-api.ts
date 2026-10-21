import { ApiError } from "@/lib/api/http";
import type { components } from "@/lib/api/schema";

type Schemas = components["schemas"];
export type AdminLifeEvent = Schemas["LifeEventView"];
export type Relation = Schemas["ProgramLinkRequest"]["relation"];
export type LifeEventRequest = Schemas["LifeEventRequest"];

export const adminLifeEventsKey = ["admin", "life-events"] as const;

export const RELATION_LABELS: Record<Relation, string> = {
  PRIMARY: "まず確認する制度",
  RELATED: "あわせて必要な手続き",
};

export type LifeEventFormValues = {
  name: string;
  icon: string;
  description: string;
  sortOrder: string;
  programs: { programCode: string; relation: Relation }[];
};

export const EMPTY_LIFE_EVENT: LifeEventFormValues = {
  name: "",
  icon: "",
  description: "",
  sortOrder: "100",
  programs: [],
};

export function toFormValues(e: AdminLifeEvent): LifeEventFormValues {
  return {
    name: e.name,
    icon: e.icon ?? "",
    description: e.description ?? "",
    sortOrder: String(e.sortOrder),
    programs: e.programs.map((p) => ({ programCode: p.code, relation: p.relation })),
  };
}

export function toRequest(v: LifeEventFormValues): LifeEventRequest {
  return {
    name: v.name.trim(),
    icon: v.icon.trim() || undefined,
    description: v.description.trim() || undefined,
    sortOrder: Number(v.sortOrder) || 0,
    programs: v.programs.filter((p) => p.programCode),
  };
}

/** 保存のエラーを利用者向けの文にする */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 412) {
    return "ほかの人が先に更新しました。画面を読み込み直してから、もう一度編集してください。";
  }
  return error instanceof ApiError ? error.detail : "処理できませんでした。時間をおいて再度お試しください。";
}
