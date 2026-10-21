import type { components } from "./schema";

/** backend の OpenAPI から生成したスキーマ（schema.d.ts）の別名 */
type Schemas = components["schemas"];

export type DocumentDetail = Schemas["DocumentDetail"];
export type ProgramDetail = Schemas["ProgramDetail"];
export type FaqDetail = Schemas["FaqDetail"];
export type ContactView = Schemas["ContactView"];
export type BasisView = Schemas["BasisView"];
export type NavigationResult = Schemas["NavigationResult"];
export type LifeEventSummary = Schemas["LifeEventSummary"];
export type KeywordResponse = Schemas["KeywordResponse"];
export type KeywordTarget = Schemas["Target"];
export type KeywordTargetType = KeywordResponse["targetType"];
export type QueryReport = Schemas["QueryReport"];
export type FeedbackSummary = Schemas["FeedbackSummary"];
export type AuditLogPage = Schemas["Page"];
export type AdminFaq = Schemas["FaqResponse"];
export type FaqContentRequest = Schemas["FaqContentRequest"];
export type Visibility = AdminFaq["visibility"];
export type ContentStatus = AdminFaq["status"];
export type CategoryNode = Schemas["CategoryNode"];
export type ProgramSummary = Schemas["ProgramSummary"];
export type DocumentSummary = Schemas["DocumentSummary"];
/** GET /api/v1/contacts（部署ごとの窓口）。画面で使う項目だけ */
export type DepartmentContacts = { code: string; name: string; contacts: { code: string; name: string }[] };
