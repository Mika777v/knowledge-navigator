import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import HomePage from "./page";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

describe("HomePage", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => vi.stubGlobal("fetch", fetchMock));
  afterEach(() => {
    fetchMock.mockReset();
    push.mockReset();
    vi.unstubAllGlobals();
  });

  it("タイトル・きっかけ・架空データの注記を表示する", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json([{ code: "MARRIAGE", name: "結婚", icon: "ring", description: "結婚したとき" }]),
    );
    renderWithQuery(<HomePage />);

    expect(screen.getByRole("heading", { level: 1, name: "社内規程ナビ" })).toBeInTheDocument();
    expect(screen.getByText(/架空企業「架空商事株式会社」/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "部署ごとの問い合わせ先の一覧" })).toHaveAttribute("href", "/contacts");
    expect(screen.getByRole("link", { name: "規程の一覧" })).toHaveAttribute("href", "/documents");
    expect(await screen.findByRole("link", { name: "結婚" })).toHaveAttribute(
      "href",
      `/search?q=${encodeURIComponent("結婚")}`,
    );
  });

  it("入力した質問で検索結果画面へ移動する（前後の空白は除く）", () => {
    fetchMock.mockResolvedValue(Response.json([]));
    renderWithQuery(<HomePage />);

    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "  PC壊れた " } });
    fireEvent.click(screen.getByRole("button", { name: "検索" }));
    expect(push).toHaveBeenCalledWith(`/search?q=${encodeURIComponent("PC壊れた")}`);
  });
});
