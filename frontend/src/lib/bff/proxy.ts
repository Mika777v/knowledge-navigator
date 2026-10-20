import { backendUrl } from "./config";

/** backend へ転送するリクエストヘッダー（許可リスト方式） */
const FORWARDED_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "content-type",
  "cookie",
  "if-match",
  "if-none-match",
  "x-xsrf-token",
];

/** ブラウザへ返さないレスポンスヘッダー（hop-by-hop と、fetch が展開済みの圧縮関連） */
const DROPPED_RESPONSE_HEADERS = new Set([
  "connection",
  "keep-alive",
  "transfer-encoding",
  "content-encoding",
  "content-length",
  "set-cookie", // 複数あり得るため getSetCookie() で個別に付け直す
]);

const TIMEOUT_MS = 60_000;

function problem(status: number, title: string, detail: string): Response {
  return Response.json(
    { type: "about:blank", title, status, detail },
    { status, headers: { "content-type": "application/problem+json" } },
  );
}

/**
 * `/api/<path>` へのリクエストを backend の `/api/<path>` へ転送する（BFF）。
 * Cookie ベースのセッションを同一オリジンで扱うため、ブラウザは backend を直接呼ばない。
 */
export async function proxyToBackend(request: Request, path: string[]): Promise<Response> {
  // パストラバーサル（/api/../actuator など）で /api 以外に到達させない
  if (path.some((segment) => segment === "." || segment === ".." || segment === "")) {
    return problem(400, "Bad Request", "不正なパスです。");
  }

  const incoming = new URL(request.url);
  const target = `${backendUrl()}/api/${path.map(encodeURIComponent).join("/")}${incoming.search}`;

  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  headers.set("x-forwarded-host", incoming.host);
  headers.set("x-forwarded-proto", incoming.protocol.replace(":", ""));
  const clientIp = request.headers.get("x-forwarded-for");
  if (clientIp) headers.set("x-forwarded-for", clientIp);

  const hasBody = !["GET", "HEAD"].includes(request.method);

  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      redirect: "manual",
      signal: AbortSignal.any([request.signal, AbortSignal.timeout(TIMEOUT_MS)]),
      // ReadableStream をそのまま送るために必要（Node.js の fetch）
      ...(hasBody ? { duplex: "half" } : {}),
    } as RequestInit);
  } catch {
    return problem(502, "Bad Gateway", "サーバーに接続できませんでした。時間をおいて再度お試しください。");
  }

  const responseHeaders = new Headers();
  upstream.headers.forEach((value, name) => {
    if (!DROPPED_RESPONSE_HEADERS.has(name)) responseHeaders.set(name, value);
  });
  for (const cookie of upstream.headers.getSetCookie()) {
    responseHeaders.append("set-cookie", cookie);
  }

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}
