import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { DocumentView } from "./document-view";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "doc-1" }),
  useSearchParams: () => search,
}));

const current = { id: "v2", versionLabel: "v2.1", effectiveFrom: "2026-04-01", changeNote: "金額を改定", current: true };
const past = { id: "v1", versionLabel: "v2.0", effectiveFrom: "2024-04-01", changeNote: null, current: false };

function detail(shown: typeof current | typeof past) {
  return {
    document: {
      id: "doc-1",
      code: "REG-GA-001",
      title: "慶弔見舞金規程",
      summary: "慶弔時の祝金・見舞金を定める。",
      documentType: "REGULATION",
      category: { slug: "ga", name: "総務" },
      ownerDepartment: { code: "GA", name: "総務部" },
      versionLabel: "v2.1",
      effectiveFrom: "2026-04-01",
      tags: ["正社員", "お金"],
    },
    currentVersionId: "v2",
    version: shown,
    versions: [current, past],
    sections: [
      { id: "s5", ordinal: 5, path: null, articleNo: "第5条", heading: "結婚祝金", content: "社員が婚姻したときは結婚祝金を支給する。" },
    ],
    programs: [{ id: "p1", code: "PRG-MARRIAGE-GIFT", name: "結婚祝金", summary: "", articleNos: ["第5条"] }],
    contacts: [
      {
        code: "GA-GENERAL",
        name: "総務部 庶務グループ",
        description: null,
        department: { code: "GA", name: "総務部" },
        email: "ga@example.com",
        phoneExt: "1310",
        chatUrl: null,
        officeHours: "平日 9:00-17:30",
        role: null,
      },
    ],
  };
}

describe("DocumentView", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    search = new URLSearchParams();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("目次・条文・この規程に基づく制度・窓口を表示する", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(detail(current)));
    renderWithQuery(<DocumentView />);

    expect(await screen.findByRole("heading", { level: 1, name: "慶弔見舞金規程" })).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "目次" })).toHaveTextContent("第5条（結婚祝金）");
    expect(screen.getByRole("link", { name: "第5条（結婚祝金）" })).toHaveAttribute("href", "#section-s5");
    expect(screen.getByRole("link", { name: "結婚祝金" })).toHaveAttribute("href", "/programs/p1");
    expect(screen.getByText("総務部 庶務グループ")).toBeInTheDocument();
    // タグから、同じタグの規程の一覧へ移動できる
    expect(screen.getByRole("link", { name: "お金" })).toHaveAttribute("href", `/documents?tag=${encodeURIComponent("お金")}`);
    expect(screen.queryByRole("note")).toBeNull();
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/documents/doc-1");
  });

  it("過去の版では注意書きと最新版へのリンクを出す", async () => {
    search = new URLSearchParams({ version: "v1" });
    fetchMock.mockResolvedValueOnce(Response.json(detail(past)));
    renderWithQuery(<DocumentView />);

    expect(await screen.findByRole("note")).toHaveTextContent("これは過去の版です");
    expect(screen.getByRole("link", { name: "最新の版" })).toHaveAttribute("href", "/documents/doc-1");
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/documents/doc-1?version=v1");
  });

  it("hl で渡された語を条文の中で強調する", async () => {
    search = new URLSearchParams([["hl", "婚姻"]]);
    fetchMock.mockResolvedValueOnce(Response.json(detail(current)));
    const { container } = renderWithQuery(<DocumentView />);

    await screen.findByRole("heading", { level: 1 });
    expect(container.querySelector("mark")?.textContent).toBe("婚姻");
  });

  it("見つからない（権限がない）ときはその旨を表示する", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ detail: "文書が見つかりません。" }, { status: 404 }));
    renderWithQuery(<DocumentView />);

    expect(await screen.findByRole("alert")).toHaveTextContent("見つかりませんでした");
  });
});
