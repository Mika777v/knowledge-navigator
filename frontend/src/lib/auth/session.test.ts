import { describe, expect, it } from "vitest";
import { safeNextPath, shouldRedirectToLogin } from "./session";

describe("shouldRedirectToLogin", () => {
  it("セッション Cookie が無ければ、ログイン画面以外はログインへ移動させる", () => {
    expect(shouldRedirectToLogin("/", false)).toBe(true);
    expect(shouldRedirectToLogin("/documents/abc", false)).toBe(true);
    expect(shouldRedirectToLogin("/login", false)).toBe(false);
  });

  it("「/login」で始まる別のパスは公開扱いにしない", () => {
    expect(shouldRedirectToLogin("/loginx", false)).toBe(true);
  });

  it("セッション Cookie があれば移動させない", () => {
    expect(shouldRedirectToLogin("/", true)).toBe(false);
  });
});

describe("safeNextPath", () => {
  it("サイト内のパスはそのまま使う", () => {
    expect(safeNextPath("/search?q=%E7%B5%90%E5%A9%9A")).toBe("/search?q=%E7%B5%90%E5%A9%9A");
  });

  it.each([null, undefined, "", "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)"])(
    "外部サイトや不正な値（%s）はホームにする",
    (next) => {
      expect(safeNextPath(next)).toBe("/");
    },
  );
});
