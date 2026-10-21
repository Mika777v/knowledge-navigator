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
