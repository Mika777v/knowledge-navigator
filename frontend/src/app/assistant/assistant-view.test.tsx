import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AssistantView } from "./assistant-view";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => search,
}));

const results = {
  searchLogId: "log-1",
  query: { raw: "結婚祝金はいくら？", normalized: "結婚祝金", terms: ["結婚祝金"], matchedLifeEvents: [] },
  programs: [],
  documents: [],
  faqs: [],
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
  relatedPrograms: [],
  fallback: false,
};

const answer = {
  answer: "結婚祝金は30,000円です。[1] 入籍日から6か月以内に申請します。[1]<script>alert(1)</script>",
  evidence: [
    {
      ref: 1,
      documentId: "d1",
      documentTitle: "慶弔見舞金規程",
      versionLabel: "v2.1",
      sectionId: "s5",
      articleNo: "第5条",
      heading: "結婚祝金",
      pageFrom: 1,
      pageTo: 1,
    },
  ],
  model: "qwen3.5:4b",
};

function sse(...events: [string, unknown][]) {
  return new Response(events.map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`).join(""), {
    headers: { "content-type": "text/event-stream" },
  });
}

describe("AssistantView", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    search = new URLSearchParams({ q: "結婚祝金はいくら？" });
    document.cookie = "XSRF-TOKEN=t; path=/";
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("回答文と、DB の値で描いた根拠・注意書き・検索結果の窓口を出す", async () => {
    fetchMock.mockResolvedValueOnce(sse(["results", results], ["answer", answer], ["done", {}]));
    const { container } = render(<AssistantView />);

    expect(await screen.findByText(/結婚祝金は30,000円です。/)).toBeInTheDocument();
    // [1] は根拠へのリンクになり、根拠は条文へリンクする
    expect(screen.getAllByRole("link", { name: "根拠 1" })[0]).toHaveAttribute("href", "#evidence-1");
    expect(screen.getByRole("link", { name: "慶弔見舞金規程 第5条（結婚祝金）" })).toHaveAttribute(
      "href",
      `/documents/d1?hl=${encodeURIComponent("結婚祝金")}#section-s5`,
    );
    expect(screen.getByText(/AI による要約です/)).toBeInTheDocument();
    expect(screen.getByText("結婚祝金の申請窓口")).toBeInTheDocument();
    // 回答文は HTML として解釈しない
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument();
  });

  it("回答できないときは理由に合った案内を出し、検索結果は残す", async () => {
    fetchMock.mockResolvedValueOnce(sse(["results", results], ["unanswerable", { reason: "DISABLED" }], ["done", {}]));
    render(<AssistantView />);

    expect(await screen.findByText(/AI による回答は、いまは使えない設定/)).toBeInTheDocument();
    expect(screen.getByText("結婚祝金の申請窓口")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "根拠" })).toBeNull();
  });

  it("知らない理由は、根拠を確かめられなかった案内にする", async () => {
    fetchMock.mockResolvedValueOnce(sse(["results", results], ["unanswerable", { reason: "VERIFICATION_FAILED" }]));
    render(<AssistantView />);

    expect(await screen.findByText(/根拠を確かめられる回答を作れませんでした/)).toBeInTheDocument();
  });

  it("通信に失敗したらエラーを出す", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("network"));
    render(<AssistantView />);

    expect(await screen.findByRole("alert")).toHaveTextContent("回答を取得できませんでした");
  });

  it("質問がなければ API を呼ばない", () => {
    search = new URLSearchParams();
    render(<AssistantView />);

    expect(screen.getByText(/知りたいことを入力してください/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
