import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { SearchResults, sectionHref } from "./search-results";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => search,
}));

const result = {
  searchLogId: "log-1",
  query: {
    raw: "結婚したら何か制度ある？",
    normalized: "結婚 制度",
    terms: ["結婚"],
    matchedLifeEvents: [{ code: "MARRIAGE", name: "結婚" }],
  },
  programs: [
    {
      id: "p1",
      code: "PRG-MARRIAGE-GIFT",
      name: "結婚祝金",
      summary: "社員が婚姻したときに支給する。",
      benefit: "30,000円",
      deadlineNote: "入籍後6か月以内",
      basis: [{ documentId: "d1", documentTitle: "慶弔見舞金規程", versionLabel: "v2.1", sectionId: "s5", articleNo: "第5条" }],
      score: 13.5,
      reasons: ["LIFE_EVENT:結婚"],
    },
  ],
  documents: [
    {
      id: "d1",
      code: "REG-GA-001",
      title: "慶弔見舞金規程",
      versionLabel: "v2.1",
      effectiveFrom: "2026-04-01",
      sections: [{ id: "s5", articleNo: "第5条", heading: "結婚祝金", snippet: "社員が結婚したときは…" }],
      score: 6.75,
    },
  ],
  faqs: [{ id: "f1", code: "FAQ-001", question: "入籍前に申請できますか？", score: 4 }],
  contacts: [
    {
      id: "c1",
      code: "GA-GENERAL",
      name: "総務部 庶務グループ",
      department: "総務部",
      phoneExt: "1310",
      email: "ga@example.com",
      chatUrl: null,
      officeHours: null,
      reason: "結婚祝金の申請窓口",
    },
  ],
  relatedPrograms: [{ id: "p9", code: "PRG-PERSONAL-INFO-CHANGE", name: "氏名・住所・扶養の変更届" }],
  fallback: false,
};

describe("SearchResults", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    search = new URLSearchParams({ q: "結婚したら何か制度ある？" });
    document.cookie = "XSRF-TOKEN=t; path=/";
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("制度・窓口・規程・FAQ・関連する手続きを表示する", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(result));
    const { container } = renderWithQuery(<SearchResults />);

    expect(await screen.findByRole("link", { name: "結婚祝金" })).toHaveAttribute("href", "/programs/p1");
    expect(fetchMock.mock.calls[0]?.[0]).toBe(`/api/v1/navigate?q=${encodeURIComponent("結婚したら何か制度ある？")}`);
    expect(screen.getByText(/「結婚」のときに使える制度/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "慶弔見舞金規程 第5条" })).toHaveAttribute("href", "/documents/d1#section-s5");
    expect(screen.getByRole("link", { name: "第5条（結婚祝金）" })).toHaveAttribute(
      "href",
      `/documents/d1?hl=${encodeURIComponent("結婚")}#section-s5`,
    );
    expect(container.querySelector("mark")?.textContent).toBe("結婚");
    expect(screen.getByText("結婚祝金の申請窓口")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "入籍前に申請できますか？" })).toHaveAttribute("href", "/faqs/f1");
    expect(screen.getByRole("link", { name: "氏名・住所・扶養の変更届" })).toBeInTheDocument();
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("結果のクリックを検索ログに記録する", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(result));
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderWithQuery(<SearchResults />);

    fireEvent.click(await screen.findByRole("link", { name: "入籍前に申請できますか？" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const [url, init] = fetchMock.mock.calls[1] ?? [];
    expect(url).toBe("/api/v1/search-logs/log-1/clicks");
    expect(JSON.parse(String(init?.body))).toEqual({ targetType: "FAQ", targetId: "f1", rank: 1 });
    expect(init?.keepalive).toBe(true);
  });

  it("見つからないときは、その旨と総合窓口を表示する", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({
        ...result,
        query: { ...result.query, matchedLifeEvents: [] },
        programs: [],
        documents: [],
        faqs: [],
        relatedPrograms: [],
        contacts: [{ ...result.contacts[0], code: "HR-REP", name: "人事部 総合窓口", reason: "該当する案内が見つからないときの総合窓口" }],
        fallback: true,
      }),
    );
    renderWithQuery(<SearchResults />);

    expect(await screen.findByRole("status")).toHaveTextContent("該当する制度・規程が見つかりませんでした");
    expect(screen.getByText("人事部 総合窓口")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "使える制度" })).toBeNull();
  });

  it("フィードバックを送れる（役に立たなかったときはコメントも）", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(result));
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderWithQuery(<SearchResults />);

    fireEvent.click(await screen.findByRole("button", { name: "いいえ" }));
    fireEvent.change(screen.getByLabelText("探していたもの（任意）"), { target: { value: "  休暇の日数 " } });
    fireEvent.click(screen.getByRole("button", { name: "送信" }));

    expect(await screen.findByText(/ご協力ありがとうございました/)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[1] ?? [];
    expect(url).toBe("/api/v1/search-logs/log-1/feedback");
    expect(JSON.parse(String(init?.body))).toEqual({ helpful: false, comment: "休暇の日数" });
  });

  it("検索語がなければ検索しない", () => {
    search = new URLSearchParams();
    renderWithQuery(<SearchResults />);
    expect(screen.getByText("知りたいことを入力してください。")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("sectionHref", () => {
  it("強調語がなければ ?hl= を付けない", () => {
    expect(sectionHref("d1", "s1", [])).toBe("/documents/d1#section-s1");
  });
});
