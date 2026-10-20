import createClient from "openapi-fetch";
import type { paths } from "./schema";

/**
 * 型付き API クライアント。ブラウザからは同一オリジンの BFF（/api/*）を呼ぶ。
 * 型（schema.d.ts）は backend の OpenAPI から `pnpm api:types` で生成する。
 */
export const api = createClient<paths>({ baseUrl: "/" });
