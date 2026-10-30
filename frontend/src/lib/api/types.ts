import type { components } from "./schema";

/** backend の OpenAPI から生成したスキーマ（schema.d.ts）の別名 */
type Schemas = components["schemas"];

export type DocumentDetail = Schemas["DocumentDetail"];
export type ProgramDetail = Schemas["ProgramDetail"];
export type FaqDetail = Schemas["FaqDetail"];
export type FaqSummary = Schemas["FaqSummary"];
export type ContactView = Schemas["ContactView"];
export type BasisView = Schemas["BasisView"];
export type NavigationResult = Schemas["NavigationResult"];
export type LifeEventSummary = Schemas["LifeEventSummary"];
export type KeywordResponse = Schemas["KeywordResponse"];
export type KeywordTarget = Schemas["Target"];
export type KeywordTargetType = KeywordResponse["targetType"];
/** 類義語のグループ（/api/v1/admin/synonyms） */
export type SynonymGroup = Schemas["SynonymGroupView"];
/** GET /api/v1/search/suggest（入力補完） */
export type SuggestionItem = Schemas["SuggestionItem"];
/** GET /api/v1/programs/{id}/recommendations */
export type RecommendedProgram = Schemas["RecommendedProgram"];
export type SimilarSection = Schemas["SimilarSection"];
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
/** GET /api/v1/tags（閲覧できる文書の件数つき） */
export type TagCount = Schemas["TagCount"];
/** GET /api/v1/contacts（部署ごとの窓口） */
export type DirectoryDepartment = Schemas["DirectoryDepartment"];
/** POST /api/v1/assistant/answers（SSE）の answer・unanswerable イベント（ADR-0013） */
export type AssistantAnswer = Schemas["AssistantAnswer"];
export type AssistantEvidence = Schemas["AssistantEvidence"];
export type AssistantUnanswerable = Schemas["AssistantUnanswerable"];
export type GuideResult = Schemas["GuideResult"];
export type GuideQuestion = Schemas["GuideQuestion"];
export type GuideProgram = Schemas["GuideProgram"];
export type GuideChecklistItem = Schemas["GuideChecklistItem"];
export type ConversationSummary = Schemas["ConversationSummary"];
export type ConversationDetail = Schemas["ConversationDetail"];
export type ConversationMessage = Schemas["ConversationMessage"];
