/**
 * 画面（HTML）と BFF の応答に付けるセキュリティヘッダー（docs/ARCHITECTURE.md §6、E16-T5）。
 *
 * CSP はナンスを使わない方式にする。ナンスは全ページを動的に描画する必要があり、部分的な事前描画（PPR）と両立しないため。
 * その代わりにスクリプト・接続先・フォームの送信先などを自分のオリジンに限り、他サイトへの埋め込み（クリックジャッキング）も禁止する。
 */
export type SecurityHeaderOptions = {
  /** next dev（React の開発用の機能が eval を使う） */
  isDev: boolean;
  /** HTTPS で公開しているとき true（HSTS と、http の読み込みを https にそろえる指定を付ける） */
  httpsOnly: boolean;
};

export function contentSecurityPolicy({ isDev, httpsOnly }: SecurityHeaderOptions): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    ...(httpsOnly ? ["upgrade-insecure-requests"] : []),
  ].join("; ");
}

export function securityHeaders(options: SecurityHeaderOptions): { key: string; value: string }[] {
  return [
    { key: "Content-Security-Policy", value: contentSecurityPolicy(options) },
    { key: "X-Content-Type-Options", value: "nosniff" },
    // CSP の frame-ancestors に対応していない古いブラウザ向け
    { key: "X-Frame-Options", value: "DENY" },
    { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
    { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
    ...(options.httpsOnly ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }] : []),
  ];
}
