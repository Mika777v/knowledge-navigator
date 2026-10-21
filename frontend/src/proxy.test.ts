import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "./proxy";

describe("proxy", () => {
  it("セッション Cookie が無ければ、元のパスを next に付けてログインへ移動させる", () => {
    const response = proxy(new NextRequest("http://localhost:3000/search?q=結婚"));
    expect(response.status).toBe(307);
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/login");
    expect(location.searchParams.get("next")).toBe("/search?q=%E7%B5%90%E5%A9%9A");
  });

  it("セッション Cookie があれば通す", () => {
    const request = new NextRequest("http://localhost:3000/", { headers: { cookie: "JSESSIONID=abc" } });
    expect(proxy(request).headers.get("location")).toBeNull();
  });

  it("ログイン画面はセッション Cookie が無くても通す", () => {
    expect(proxy(new NextRequest("http://localhost:3000/login")).headers.get("location")).toBeNull();
  });
});
