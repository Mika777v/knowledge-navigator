import type { components } from "@/lib/api/schema";
import { apiFetch } from "@/lib/api/http";
import { ifMatch } from "@/lib/content-labels";

type Schemas = components["schemas"];
export type AdminProgram = Schemas["ProgramResponse"];
export type ContactRole = Schemas["ContactRequest"]["role"];

export const adminProgramsKey = ["admin", "programs"] as const;

export const CONTACT_ROLE_LABELS: Record<ContactRole, string> = {
  APPLY: "申請先",
  INQUIRY: "問い合わせ先",
  PAYMENT: "支給窓口",
};

/** 制度の入力欄の値 */
export type ProgramFormValues = {
  name: string;
  nameKana: string;
  summary: string;
  eligibility: string;
  benefit: string;
  howToApply: string;
  deadlineNote: string;
  requiredDocuments: string;
  categorySlug: string;
  visibility: AdminProgram["visibility"];
  /** 根拠の条（条番号が空なら文書全体） */
  basis: { documentCode: string; articleNo: string }[];
  contacts: { contactCode: string; role: ContactRole }[];
};

export const EMPTY_PROGRAM: ProgramFormValues = {
  name: "",
  nameKana: "",
  summary: "",
  eligibility: "",
  benefit: "",
  howToApply: "",
  deadlineNote: "",
  requiredDocuments: "",
  categorySlug: "",
  visibility: "INTERNAL",
  basis: [],
  contacts: [],
};

export function toFormValues(p: AdminProgram): ProgramFormValues {
  return {
    name: p.name,
    nameKana: p.nameKana ?? "",
    summary: p.summary,
    eligibility: p.eligibility ?? "",
    benefit: p.benefit ?? "",
    howToApply: p.howToApply ?? "",
    deadlineNote: p.deadlineNote ?? "",
    requiredDocuments: p.requiredDocuments ?? "",
    categorySlug: p.category?.slug ?? "",
    visibility: p.visibility,
    basis: p.basis.map((b) => ({ documentCode: b.documentCode, articleNo: b.articleNo ?? "" })),
    contacts: p.contacts.map((c) => ({ contactCode: c.code, role: c.role })),
  };
}

/** 説明の部分（PUT /admin/programs/{id} と作成時の attributes）。空の項目は送らない */
export function toAttributes(v: ProgramFormValues) {
  const optional = (s: string) => (s.trim() ? s : undefined);
  return {
    name: v.name.trim(),
    nameKana: optional(v.nameKana),
    summary: v.summary,
    eligibility: optional(v.eligibility),
    benefit: optional(v.benefit),
    howToApply: optional(v.howToApply),
    deadlineNote: optional(v.deadlineNote),
    requiredDocuments: optional(v.requiredDocuments),
    categorySlug: v.categorySlug,
    visibility: v.visibility,
  };
}

function path(id: string) {
  return `/api/v1/admin/programs/${encodeURIComponent(id)}`;
}

/**
 * 根拠と窓口を、選ばれたものに置き換える（空の行は送らない）。
 * それぞれの更新で版番号が上がるため、前の応答の版番号を次の If-Match に使う。
 */
export async function saveRelations(program: AdminProgram, v: ProgramFormValues): Promise<AdminProgram> {
  const withBasis = await apiFetch<AdminProgram>(`${path(program.id)}/basis`, {
    method: "PUT",
    headers: ifMatch(program.rowVersion),
    json: {
      basis: v.basis
        .filter((b) => b.documentCode)
        .map((b) => ({ documentCode: b.documentCode, articleNo: b.articleNo || undefined })),
    },
  });
  return apiFetch<AdminProgram>(`${path(program.id)}/contacts`, {
    method: "PUT",
    headers: ifMatch(withBasis.rowVersion),
    json: { contacts: v.contacts.filter((c) => c.contactCode) },
  });
}

/** 説明・根拠・窓口をまとめて保存する */
export async function saveProgram(program: AdminProgram, v: ProgramFormValues): Promise<AdminProgram> {
  const updated = await apiFetch<AdminProgram>(path(program.id), {
    method: "PUT",
    headers: ifMatch(program.rowVersion),
    json: toAttributes(v),
  });
  return saveRelations(updated, v);
}

export function transition(program: AdminProgram, action: "publish" | "archive"): Promise<AdminProgram> {
  return apiFetch<AdminProgram>(`${path(program.id)}/${action}`, {
    method: "POST",
    headers: ifMatch(program.rowVersion),
  });
}
