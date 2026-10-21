import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { AdminGuard } from "./admin-guard";

function me(role: string) {
  return Response.json({ id: "u", email: "x@example.com", displayName: "X", department: null, role, employmentType: "REGULAR", joinedOn: "2026-04-01" });
}

describe("AdminGuard", () => {
  afterEach(() => vi.unstubAllGlobals());

  it.each(["CONTENT_EDITOR", "SYSTEM_ADMIN"])("%s には管理画面を表示する", async (role) => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(me(role)));
    renderWithQuery(<AdminGuard>管理の中身</AdminGuard>);
    expect(await screen.findByText("管理の中身")).toBeInTheDocument();
  });

  it.each(["EMPLOYEE", "MANAGER"])("%s には表示しない", async (role) => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(me(role)));
    renderWithQuery(<AdminGuard>管理の中身</AdminGuard>);
    expect(await screen.findByRole("alert")).toHaveTextContent("コンテンツ担当者とシステム管理者だけ");
    expect(screen.queryByText("管理の中身")).toBeNull();
  });
});
