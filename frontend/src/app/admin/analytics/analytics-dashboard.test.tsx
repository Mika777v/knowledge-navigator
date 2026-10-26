import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { AnalyticsDashboard } from "./analytics-dashboard";

const period = { from: "2026-09-22", to: "2026-10-21" };
const stat = (query: string, over: Record<string, number> = {}) => ({
  query,
  searches: 4,
  users: 3,
  zeroHitSearches: 1,
  clickedSearches: 2,
  lastSearchedAt: "2026-10-21T03:00:00Z",
  ...over,
});

function stubApi(role = "CONTENT_EDITOR") {
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.endsWith("/api/v1/me")) {
      return Response.json({ id: "u", email: "x@example.com", displayName: "X", department: null, role, employmentType: "REGULAR", joinedOn: "2026-04-01" });
    }
    if (url.includes("/zero-hits")) {
      return Response.json({ period, minDistinctUsers: 2, queries: [stat("社員食堂", { zeroHitSearches: 4 })] });
    }
    if (url.includes("/top-queries")) {
      return Response.json({ period, minDistinctUsers: 2, queries: [stat("結婚"), stat("社員食堂", { zeroHitSearches: 4 })] });
    }
    return Response.json({
      period,
      helpful: 3,
      notHelpful: 1,
      comments: [{ query: "社員食堂", helpful: false, comment: "営業時間が知りたい", createdAt: "2026-10-21T03:00:00Z" }],
    });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("AnalyticsDashboard", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("何も見つからなかった質問から、類義語・FAQ の登録へ進める（FAQ はコンテンツ担当者だけ）", async () => {
    stubApi("CONTENT_EDITOR");
    renderWithQuery(<AnalyticsDashboard />);

    const zero = await screen.findByRole("region", { name: "何も見つからなかった質問" });
    expect(await within(zero).findByRole("link", { name: "類義語に登録" })).toHaveAttribute(
      "href",
      `/admin/synonyms?term=${encodeURIComponent("社員食堂")}`,
    );
    expect(await within(zero).findByRole("link", { name: "FAQ を作る" })).toHaveAttribute(
      "href",
      `/admin/faqs/new?question=${encodeURIComponent("社員食堂")}`,
    );
  });

  it("システム管理者には FAQ を作るリンクを出さない", async () => {
    stubApi("SYSTEM_ADMIN");
    renderWithQuery(<AnalyticsDashboard />);

    const zero = await screen.findByRole("region", { name: "何も見つからなかった質問" });
    await within(zero).findByRole("link", { name: "類義語に登録" });
    await waitFor(() => expect(within(zero).queryByRole("link", { name: "FAQ を作る" })).toBeNull());
  });

  it("ゼロヒット・人気の質問・フィードバックを表示する", async () => {
    stubApi();
    renderWithQuery(<AnalyticsDashboard />);

    const zero = await screen.findByRole("region", { name: "何も見つからなかった質問" });
    expect(await within(zero).findByText("社員食堂")).toBeInTheDocument();
    expect(within(zero).getByRole("link", { name: "辞書で確認" })).toHaveAttribute(
      "href",
      `/admin/dictionary?term=${encodeURIComponent("社員食堂")}`,
    );
    expect(screen.getByText(/2人以上が検索した質問だけ/)).toBeInTheDocument();

    const top = screen.getByRole("region", { name: "よく検索された質問" });
    await within(top).findByRole("link", { name: "結婚" });
    await screen.findByText("営業時間が知りたい");
    const marriage = within(top).getByRole("link", { name: "結婚" }).closest("tr")!;
    expect(within(marriage).getByText("25%")).toBeInTheDocument();
    expect(within(marriage).getByText("50%")).toBeInTheDocument();

    expect(screen.getByText(/役に立った割合 75%/)).toBeInTheDocument();
    expect(screen.getByText("営業時間が知りたい")).toBeInTheDocument();
  });

  it("期間を指定して読み直す", async () => {
    const fetchMock = stubApi();
    renderWithQuery(<AnalyticsDashboard />);
    await screen.findByText("営業時間が知りたい");

    fireEvent.change(screen.getByLabelText("開始日"), { target: { value: "2026-10-01" } });
    fireEvent.change(screen.getByLabelText("終了日"), { target: { value: "2026-10-15" } });
    fireEvent.click(screen.getByRole("button", { name: "表示" }));

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes("from=2026-10-01&to=2026-10-15"))).toBe(true),
    );
  });

  it("権限がないときは backend の説明を表示する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () =>
        Response.json({ detail: "検索の分析を見られるのはコンテンツ担当者とシステム管理者だけです。" }, { status: 403 }),
      ),
    );
    renderWithQuery(<AnalyticsDashboard />);
    expect((await screen.findAllByRole("alert"))[0]).toHaveTextContent("コンテンツ担当者とシステム管理者だけ");
  });
});
