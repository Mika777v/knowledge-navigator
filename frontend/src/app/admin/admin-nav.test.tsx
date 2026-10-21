import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { AdminNav } from "./admin-nav";

vi.mock("next/navigation", () => ({ usePathname: () => "/admin/analytics" }));

function me(role: string) {
  return Response.json({ id: "u", email: "x@example.com", displayName: "X", department: null, role, employmentType: "REGULAR", joinedOn: "2026-04-01" });
}

describe("AdminNav", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("コンテンツ担当者には監査ログを出さない", async () => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(me("CONTENT_EDITOR")));
    renderWithQuery(<AdminNav />);
    expect(await screen.findByRole("link", { name: "検索の分析" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "キーワード辞書" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "監査ログ" })).toBeNull();
  });

  it("システム管理者にはすべて出す", async () => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(me("SYSTEM_ADMIN")));
    renderWithQuery(<AdminNav />);
    expect(await screen.findByRole("link", { name: "監査ログ" })).toBeInTheDocument();
  });
});
