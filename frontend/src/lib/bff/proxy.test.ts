// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { proxyToBackend } from "./proxy";

describe("proxyToBackend", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubEnv("BACKEND_URL", "http://backend:8080/");
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("パスとクエリを backend の /api 配下へ転送する", async () => {
    fetchMock.mockResolvedValue(new Response("{}", { status: 200 }));

    await proxyToBackend(new Request("http://localhost:3000/api/v1/navigate?q=%E7%B5%90%E5%A9%9A"), [
      "v1",
      "navigate",
    ]);

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://backend:8080/api/v1/navigate?q=%E7%B5%90%E5%A9%9A");
    expect(init.method).toBe("GET");
    expect(init.body).toBeUndefined();
  });

  it("許可リストにあるヘッダーだけを転送する", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await proxyToBackend(
      new Request("http://localhost:3000/api/v1/me", {
        headers: { cookie: "SESSION=abc", authorization: "Bearer leaked", "x-xsrf-token": "t" },
      }),
      ["v1", "me"],
    );

    const headers: Headers = fetchMock.mock.calls[0][1].headers;
    expect(headers.get("cookie")).toBe("SESSION=abc");
    expect(headers.get("x-xsrf-token")).toBe("t");
    expect(headers.get("authorization")).toBeNull();
    expect(headers.get("x-forwarded-host")).toBe("localhost:3000");
  });

  it("複数の Set-Cookie をそのまま返す", async () => {
    const upstreamHeaders = new Headers();
    upstreamHeaders.append("set-cookie", "SESSION=abc; HttpOnly");
    upstreamHeaders.append("set-cookie", "XSRF-TOKEN=t");
    upstreamHeaders.set("content-type", "application/json");
    fetchMock.mockResolvedValue(new Response("{}", { status: 200, headers: upstreamHeaders }));

    const res = await proxyToBackend(
      new Request("http://localhost:3000/api/auth/login", { method: "POST", body: "{}" }),
      ["auth", "login"],
    );

    expect(res.status).toBe(200);
    expect(res.headers.getSetCookie()).toEqual(["SESSION=abc; HttpOnly", "XSRF-TOKEN=t"]);
    expect(res.headers.get("content-type")).toBe("application/json");
  });

  it("パストラバーサルを拒否し backend を呼ばない", async () => {
    const res = await proxyToBackend(new Request("http://localhost:3000/api/x"), ["..", "actuator", "env"]);

    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("backend に接続できないときは内部情報を出さず 502 を返す", async () => {
    fetchMock.mockRejectedValue(new Error("connect ECONNREFUSED 10.0.0.5:8080"));

    const res = await proxyToBackend(new Request("http://localhost:3000/api/v1/me"), ["v1", "me"]);

    expect(res.status).toBe(502);
    const body = await res.text();
    expect(body).not.toContain("ECONNREFUSED");
  });
});
