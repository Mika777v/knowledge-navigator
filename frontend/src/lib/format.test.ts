import { describe, expect, it } from "vitest";
import { formatDateTime, formatRatio } from "./format";

describe("format", () => {
  it("日時を日本時間で表示する", () => {
    expect(formatDateTime("2026-10-21T15:30:00Z")).toBe("2026/10/22 00:30");
  });

  it("割合を百分率にする（分母 0 は —）", () => {
    expect(formatRatio(1, 3)).toBe("33%");
    expect(formatRatio(0, 0)).toBe("—");
  });
});
