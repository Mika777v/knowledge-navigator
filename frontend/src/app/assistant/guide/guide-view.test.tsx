import { fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { GuideView } from "./guide-view";

vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams("event=MARRIAGE") }));

const employment = {
  code: "EMPLOYMENT_TYPE",
  text: "雇用形態を教えてください。",
  choices: [
    { value: "REGULAR", label: "正社員" },
    { value: "PART_TIME", label: "パートタイム社員" },
  ],
};
const contact = {
  code: "GA-GENERAL",
  name: "総務部 庶務グループ",
  department: "総務部",
  email: null,
  phoneExt: "1310",
  chatUrl: null,
  officeHours: null,
  role: "APPLY",
};
const gift = {
  id: "p1",
  code: "PRG-MARRIAGE-GIFT",
  name: "結婚祝金",
  summary: "結婚したときのお祝い金。",
  relation: "PRIMARY",
  eligibility: "勤続6か月以上の正社員・契約社員",
  unmetQuestions: [],
  unansweredQuestions: [],
  basis: [{ documentId: "d1", documentTitle: "慶弔見舞金規程", sectionId: "s5", articleNo: "第5条", heading: "結婚祝金" }],
  contacts: [contact],
};
const housing = { ...gift, id: "p2", code: "PRG-HOUSING-ALLOWANCE", name: "住宅手当", relation: "RELATED", basis: [] };

function result(answer: string, source: string) {
  const partTime = answer === "PART_TIME";
  return {
    lifeEvent: { code: "MARRIAGE", name: "結婚", description: "結婚したときの制度。" },
    questions: [{ ...employment, answer, answerSource: source }],
    programs: [
      { ...gift, status: "ELIGIBLE" },
      partTime
        ? { ...housing, status: "NOT_ELIGIBLE", unmetQuestions: ["EMPLOYMENT_TYPE"] }
        : { ...housing, status: "ELIGIBLE" },
    ],
    checklist: [
      {
        programId: "p1",
        programName: "結婚祝金",
        status: "ELIGIBLE",
        requiredDocuments: "婚姻届受理証明書の写し",
        deadlineNote: "入籍日から6か月以内",
        howToApply: null,
      },
      ...(partTime
        ? []
        : [
            {
              programId: "p2",
              programName: "住宅手当",
              status: "ELIGIBLE" as const,
              requiredDocuments: null,
              deadlineNote: null,
              howToApply: null,
            },
          ]),
    ],
  };
}

describe("GuideView", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("プロフィールから選んだ答えで判定し、根拠・チェックリスト・申請先を出す", async () => {
    fetchMock.mockResolvedValue(Response.json(result("REGULAR", "PROFILE")));
    renderWithQuery(<GuideView />);

    expect(await screen.findByRole("heading", { level: 1, name: "結婚の手続き" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "正社員" })).toBeChecked();
    expect(screen.getByText(/登録されている情報から選んでいます/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "慶弔見舞金規程 第5条（結婚祝金）" })).toHaveAttribute(
      "href",
      "/documents/d1#section-s5",
    );
    const checklist = screen.getByRole("region", { name: "手続きのチェックリスト" });
    expect(within(checklist).getByText("入籍日から6か月以内")).toBeInTheDocument();
    expect(within(checklist).getAllByText("総務部 庶務グループ")).toHaveLength(2);
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(String(init?.body))).toEqual({ lifeEvent: "MARRIAGE", answers: {} });
  });

  it("答えを選び直すと送り直し、対象外の制度は理由つきで残してチェックリストから外す", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json(result("REGULAR", "PROFILE")))
      .mockResolvedValueOnce(Response.json(result("PART_TIME", "USER")));
    renderWithQuery(<GuideView />);

    fireEvent.click(await screen.findByRole("radio", { name: "パートタイム社員" }));

    const housing = await screen.findByRole("listitem", { name: "住宅手当" });
    expect(await within(housing).findByText("対象外")).toBeInTheDocument();
    expect(within(housing).getByText(/当てはまらない条件: 雇用形態を教えてください。/)).toBeInTheDocument();
    const checklist = screen.getByRole("region", { name: "手続きのチェックリスト" });
    expect(within(checklist).queryByText("住宅手当")).toBeNull();
    const [, init] = fetchMock.mock.calls[1];
    expect(JSON.parse(String(init?.body))).toEqual({ lifeEvent: "MARRIAGE", answers: { EMPLOYMENT_TYPE: "PART_TIME" } });
  });
});
