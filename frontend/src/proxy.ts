import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE, shouldRedirectToLogin } from "@/lib/auth/session";

/**
 * セッション Cookie が無ければログイン画面へ移動させる（楽観的な確認）。
 * Cookie の有効性やデータの閲覧権限は backend が判定する。ここでは DB や backend に問い合わせない。
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (shouldRedirectToLogin(pathname, request.cookies.has(SESSION_COOKIE))) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname + search);
    return NextResponse.redirect(login);
  }
  return NextResponse.next();
}

export const config = {
  // API（BFF）・ビルド成果物・画像などは対象外にする
  matcher: ["/((?!api/|_next/static|_next/image|favicon.ico).*)"],
};
