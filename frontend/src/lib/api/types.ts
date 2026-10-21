import type { components } from "./schema";

/** backend の OpenAPI から生成したスキーマ（schema.d.ts）の別名 */
type Schemas = components["schemas"];

export type DocumentDetail = Schemas["DocumentDetail"];
export type ProgramDetail = Schemas["ProgramDetail"];
export type FaqDetail = Schemas["FaqDetail"];
export type ContactView = Schemas["ContactView"];
export type BasisView = Schemas["BasisView"];
