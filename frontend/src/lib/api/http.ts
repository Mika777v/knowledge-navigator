/** BFF（同一オリジンの /api/*）を呼ぶための fetch。Cookie のセッションと CSRF トークンを扱う */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
  ) {
    super(detail);
    this.name = "ApiError";
  }
}

const XSRF_COOKIE = "XSRF-TOKEN";
const XSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/** backend が発行した CSRF トークン（HttpOnly ではない Cookie）を読む */
export function readXsrfToken(cookie: string = typeof document === "undefined" ? "" : document.cookie): string | null {
  for (const part of cookie.split(";")) {
    const [name, ...rest] = part.trim().split("=");
    if (name === XSRF_COOKIE) return decodeURIComponent(rest.join("="));
  }
  return null;
}

/** 更新系のリクエストの前に CSRF トークンを用意する（Cookie が無ければ発行してもらう） */
export async function ensureXsrfToken(): Promise<string> {
  const existing = readXsrfToken();
  if (existing) return existing;
  await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
  const issued = readXsrfToken();
  if (!issued) throw new ApiError(500, "CSRF トークンを取得できませんでした。");
  return issued;
}

/**
 * JSON の API を呼ぶ。失敗時は Problem Details の detail を持つ ApiError を投げる。
 * 401（未ログイン・セッション切れ）は onUnauthorized を呼ぶ（既定はログイン画面へ移動）。
 */
export async function apiFetch<T>(
  path: string,
  init: RequestInit & { json?: unknown } = {},
  onUnauthorized: () => void = redirectToLogin,
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.json !== undefined) headers.set("Content-Type", "application/json");
  if (!SAFE_METHODS.has(method)) headers.set(XSRF_HEADER, await ensureXsrfToken());

  const response = await fetch(path, {
    ...init,
    method,
    headers,
    body: init.json !== undefined ? JSON.stringify(init.json) : init.body,
    credentials: "same-origin",
  });

  if (response.status === 401) {
    onUnauthorized();
    throw new ApiError(401, "ログインが必要です。");
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response));
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function problemDetail(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string };
    if (body.detail) return body.detail;
  } catch {
    // 本文が JSON でない場合は既定のメッセージにする
  }
  return response.status >= 500
    ? "サーバーでエラーが発生しました。時間をおいて再度お試しください。"
    : "リクエストを処理できませんでした。";
}

function redirectToLogin() {
  if (typeof window === "undefined") return;
  const next = window.location.pathname + window.location.search;
  // セッション切れなので、画面の状態ごと捨てるために再読み込みを伴う移動にする
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`/login?next=${encodeURIComponent(next)}`);
}
