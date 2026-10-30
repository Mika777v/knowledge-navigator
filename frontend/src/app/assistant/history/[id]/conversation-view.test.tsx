import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { ConversationView } from "./conversation-view";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "c1" }), useRouter: () => ({ push }) }));

const conversation = {
  id: "c1",
  title: "結婚祝金はいくら？",
  lastMessageAt: "2026-10-30T01:00:00Z",
  expiresAt: "2027-01-28T01:00:00Z",
  messages: [
    {
      role: "USER",
      content: "結婚祝金はいくら？",
      answerable: null,
      unanswerableReason: null,
      model: null,
      createdAt: "2026-10-30T01:00:00Z",
      evidence: [],
      hiddenEvidenceCount: 0,
    },
    {
      role: "ASSISTANT",
      content: "30,000円です。[1][2]",
      answerable: true,
      unanswerableReason: null,
      model: "qwen3.5:4b",
      createdAt: "2026-10-30T01:00:00Z",
      evidence: [
        {
          ref: 1,
          documentId: "d1",
          documentTitle: "慶弔見舞金規程",
          versionLabel: "v2.0",
          current: false,
          sectionId: "s5",
          articleNo: "第5条",
          heading: "結婚祝金",
          pageFrom: null,
          pageTo: null,
        },
      ],
      hiddenEvidenceCount: 1,
    },
    {
      role: "USER",
      content: "申請先は？",
      answerable: null,
      unanswerableReason: null,
      model: null,
      createdAt: "2026-10-30T01:01:00Z",
      evidence: [],
      hiddenEvidenceCount: 0,
    },
    {
      role: "ASSISTANT",
      content: "",
      answerable: false,
      unanswerableReason: "NO_EVIDENCE",
      model: null,
      createdAt: "2026-10-30T01:01:00Z",
      evidence: [],
      hiddenEvidenceCount: 0,
    },
  ],
};

describe("ConversationView", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => {
    fetchMock.mockReset();
    push.mockReset();
    vi.unstubAllGlobals();
  });

  it("質問と回答を順に出し、今も見られる根拠だけをリンクにして、見られない根拠は数だけ出す", async () => {
    fetchMock.mockResolvedValue(Response.json(conversation));
    renderWithQuery(<ConversationView />);

    expect(await screen.findByRole("heading", { level: 1, name: "結婚祝金はいくら？" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "根拠 1" })).toHaveAttribute("href", "#evidence-1");
    // 見られなくなった [2] はリンクにしない
    expect(screen.queryByRole("link", { name: "根拠 2" })).toBeNull();
    expect(screen.getByText("いまは見られない根拠が 1 件あります。")).toBeInTheDocument();
    expect(screen.getByText(/いまは改定されています/)).toBeInTheDocument();
    expect(screen.getByText(/規程の中に、この質問の答えが見つかりませんでした/)).toBeInTheDocument();
  });

  it("確かめてから削除し、一覧へ戻る", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json(conversation))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("confirm", () => true);
    renderWithQuery(<ConversationView />);

    fireEvent.click(await screen.findByRole("button", { name: "この会話を削除する" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/assistant/history"));
    const [url, init] = fetchMock.mock.calls[1];
    expect(String(url)).toBe("/api/v1/assistant/conversations/c1");
    expect(init?.method).toBe("DELETE");
  });
});
