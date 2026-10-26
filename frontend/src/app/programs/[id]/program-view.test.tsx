import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { ProgramView } from "./program-view";

vi.mock("next/navigation", () => ({ useParams: () => ({ id: "p1" }) }));

const program = {
  program: {
    id: "p1",
    code: "PRG-MARRIAGE-GIFT",
    name: "結婚祝金",
    summary: "社員が婚姻したときに支給する。",
    benefit: "30,000円",
    deadlineNote: "入籍後6か月以内",
    category: { slug: "welfare", name: "福利厚生" },
  },
  nameKana: "けっこんいわいきん",
  eligibility: "勤続6か月以上の社員",
  howToApply: "総務部に申請書を提出する。",
  requiredDocuments: null,
  ownerDepartment: { code: "GA", name: "総務部" },
  basis: [
    { documentId: "doc-1", documentCode: "REG-GA-001", documentTitle: "慶弔見舞金規程", sectionId: "s5", articleNo: "第5条", heading: "結婚祝金" },
    { documentId: "doc-2", documentCode: "REG-HR-001", documentTitle: "就業規則", sectionId: null, articleNo: null, heading: null },
  ],
  contacts: [
    {
      code: "GA-GENERAL",
      name: "総務部 庶務グループ",
      description: null,
      department: { code: "GA", name: "総務部" },
      email: null,
      phoneExt: "1310",
      chatUrl: null,
      officeHours: null,
      role: "APPLY",
    },
  ],
  faqs: [{ id: "f1", code: "FAQ-001", question: "入籍前に申請できますか？", category: null }],
  lifeEvents: [{ code: "MARRIAGE", name: "結婚", icon: "ring", description: "" }],
};

describe("ProgramView", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => vi.stubGlobal("fetch", fetchMock));
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  /** 制度の詳細と推薦を URL で分けて返す */
  function respond(recommendations: unknown[]) {
    fetchMock.mockImplementation(async (input) =>
      Response.json(String(input).endsWith("/recommendations") ? recommendations : program),
    );
  }

  it("対象者・内容・期限と、根拠の条文・窓口・FAQ を表示する", async () => {
    respond([]);
    renderWithQuery(<ProgramView />);

    expect(await screen.findByRole("heading", { level: 1, name: "結婚祝金" })).toBeInTheDocument();
    expect(screen.getByText("勤続6か月以上の社員")).toBeInTheDocument();
    expect(screen.getByText("入籍後6か月以内")).toBeInTheDocument();
    expect(screen.queryByText("必要書類")).toBeNull();
    expect(screen.getByRole("link", { name: "慶弔見舞金規程 第5条（結婚祝金）" })).toHaveAttribute(
      "href",
      "/documents/doc-1#section-s5",
    );
    expect(screen.getByRole("link", { name: "就業規則" })).toHaveAttribute("href", "/documents/doc-2");
    expect(screen.getByText("申請先")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "入籍前に申請できますか？" })).toHaveAttribute("href", "/faqs/f1");
    // 推薦がなければ見出しも出さない
    expect(screen.queryByRole("heading", { name: "この制度を見た人はこちらも" })).toBeNull();
  });

  it("あわせて見られている制度と、同じきっかけの制度を理由つきで出す", async () => {
    respond([
      { id: "p2", code: "PRG-PC-REPAIR", name: "PC の故障・代替機", reason: "CO_CLICK", lifeEvent: null },
      { id: "p3", code: "PRG-MARRIAGE-LEAVE", name: "結婚休暇", reason: "LIFE_EVENT", lifeEvent: "結婚" },
    ]);
    renderWithQuery(<ProgramView />);

    expect(await screen.findByRole("heading", { name: "この制度を見た人はこちらも" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "結婚休暇" })).toHaveAttribute("href", "/programs/p3");
    expect(screen.getByText("結婚のときに")).toBeInTheDocument();
    expect(screen.getByText("あわせて見られています")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/programs/p1/recommendations", expect.anything());
  });
});
