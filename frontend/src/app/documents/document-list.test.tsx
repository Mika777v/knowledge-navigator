import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { DocumentList } from "./document-list";

let query = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(query) }));

const doc = (code: string, title: string, tags: string[]) => ({
  id: code.toLowerCase(),
  code,
  title,
  summary: `${title}の概要`,
  documentType: "REGULATION",
  category: { slug: "c", name: "カテゴリ" },
  ownerDepartment: { code: "HR", name: "人事部" },
  versionLabel: "v1.0",
  effectiveFrom: "2026-04-01",
  tags,
});

function stubApi() {
  const fetch = vi.fn<typeof globalThis.fetch>(async (input) => {
    const url = String(input);
    if (url === "/api/v1/tags")
      return Response.json([
        { name: "正社員", type: "TARGET", documentCount: 1 },
        { name: "お金", type: "TOPIC", documentCount: 2 },
      ]);
    if (url.startsWith("/api/v1/documents"))
      return Response.json(
        url.includes("tag=")
          ? [doc("REG-HR-004", "通勤手当規程", ["お金"])]
          : [doc("REG-HR-001", "就業規則", ["正社員"]), doc("REG-HR-004", "通勤手当規程", ["お金"])],
      );
    throw new Error(`unexpected ${url}`);
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("DocumentList", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("規程とタグの一覧を出し、タグには閲覧できる規程の件数を付ける", async () => {
    query = "";
    stubApi();
    renderWithQuery(<DocumentList />);

    expect(await screen.findByRole("link", { name: "就業規則" })).toHaveAttribute("href", "/documents/reg-hr-001");
    const filter = screen.getByRole("navigation", { name: "タグで絞り込む" });
    expect(await screen.findByRole("link", { name: "お金（2）" })).toHaveAttribute(
      "href",
      `/documents?tag=${encodeURIComponent("お金")}`,
    );
    expect(filter).toHaveTextContent("対象者");
  });

  it("?tag= で絞り込み、選んだタグをもう一度押すと解除できる", async () => {
    query = `tag=${encodeURIComponent("お金")}`;
    const fetch = stubApi();
    renderWithQuery(<DocumentList />);

    expect(await screen.findByRole("link", { name: "通勤手当規程" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "就業規則" })).toBeNull();
    expect(screen.getByText(/「お金」のタグが付いた規程を表示しています。/)).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "お金（2）" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "お金（2）" })).toHaveAttribute("href", "/documents");
    expect(fetch.mock.calls.some(([u]) => String(u) === `/api/v1/documents?tag=${encodeURIComponent("お金")}`)).toBe(
      true,
    );
  });
});
