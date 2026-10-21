import { describe, expect, it } from "vitest";
import { contentSecurityPolicy, securityHeaders } from "./security-headers";

describe("securityHeaders", () => {
  it("本番の CSP は eval を許さず、他サイトへの埋め込みと外部への接続を禁止する", () => {
    const csp = contentSecurityPolicy({ isDev: false, httpsOnly: false });
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("connect-src 'self'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).not.toContain("upgrade-insecure-requests");
  });

  it("開発時だけ eval を許す", () => {
    expect(contentSecurityPolicy({ isDev: true, httpsOnly: false })).toContain("'unsafe-eval'");
  });

  it("HTTPS で公開するときだけ HSTS を付ける", () => {
    const keys = (httpsOnly: boolean) => securityHeaders({ isDev: false, httpsOnly }).map((h) => h.key);
    expect(keys(false)).not.toContain("Strict-Transport-Security");
    expect(keys(true)).toContain("Strict-Transport-Security");
    expect(contentSecurityPolicy({ isDev: false, httpsOnly: true })).toContain("upgrade-insecure-requests");
    expect(keys(false)).toEqual(
      expect.arrayContaining(["Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy"]),
    );
  });
});
