/** ログイン状態の判定（proxy.ts での「楽観的な」確認に使う。本当の認証は backend が行う） */

/** backend（Spring Boot / Tomcat）のセッション Cookie 名 */
export const SESSION_COOKIE = "JSESSIONID";

/** ログインしていなくても開けるパス */
const PUBLIC_PATHS = ["/login"];

/** ログイン画面へ移動させるべきか。セッション Cookie が無く、公開されていないページを開こうとしている場合 */
export function shouldRedirectToLogin(pathname: string, hasSessionCookie: boolean): boolean {
  if (hasSessionCookie) return false;
  return !PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/** ログイン後の移動先。外部サイトへの移動（オープンリダイレクト）を防ぐため、同じサイト内のパスだけを許可する */
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return "/";
  return next;
}
