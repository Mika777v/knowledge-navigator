import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, readXsrfToken } from "./http";

function clearCookies() {
  for (const c of document.cookie.split(";")) {
    const name = c.split("=")[0]?.trim();
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  }
}

describe("readXsrfToken", () => {
  it("XSRF-TOKEN Cookie の値を読む", () => {
    expect(readXsrfToken("a=1; XSRF-TOKEN=abc%3D; b=2")).toBe("abc=");
    expect(readXsrfToken("a=1")).toBeNull();
  });
});

describe("apiFetch", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    clearCookies();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("GET では CSRF ヘッダーを付けない", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ ok: true }));
    await expect(apiFetch("/api/v1/me")).resolves.toEqual({ ok: true });
    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.has("X-XSRF-TOKEN")).toBe(false);
  });

  it("POST では Cookie の CSRF トークンをヘッダーに付け、JSON を送る", async () => {
    document.cookie = "XSRF-TOKEN=token-1; path=/";
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await apiFetch("/api/v1/auth/logout", { method: "POST", json: { a: 1 } });
    const init = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get("X-XSRF-TOKEN")).toBe("token-1");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(init?.body).toBe('{"a":1}');
  });

  it("CSRF Cookie が無ければ先に /auth/csrf で発行してもらう", async () => {
    fetchMock.mockImplementationOnce(async () => {
      document.cookie = "XSRF-TOKEN=issued; path=/";
      return new Response(null, { status: 204 });
    });
    fetchMock.mockResolvedValueOnce(Response.json({}));
    await apiFetch("/api/v1/auth/login", { method: "POST", json: {} });
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/auth/csrf");
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get("X-XSRF-TOKEN")).toBe("issued");
  });

  it("401 では onUnauthorized を呼んで ApiError を投げる", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    const onUnauthorized = vi.fn();
    await expect(apiFetch("/api/v1/me", {}, onUnauthorized)).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("エラー時は Problem Details の detail をメッセージにする", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ detail: "見つかりません" }, { status: 404 }));
    const error = await apiFetch("/api/v1/documents/x").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).detail).toBe("見つかりません");
  });
});
