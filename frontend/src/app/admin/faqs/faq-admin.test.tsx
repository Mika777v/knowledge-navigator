import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminFaq } from "@/lib/api/types";
import { renderWithQuery } from "@/test/render";
import { EditFaq } from "./[id]/edit-faq";
import { toContentRequest, toFormValues } from "./faq-form";
import { NewFaq } from "./new/new-faq";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "f1" }),
  useRouter: () => ({ push }),
  useSearchParams: () => search,
}));

let search = new URLSearchParams();

const faq = (over: Partial<AdminFaq> = {}): AdminFaq => ({
  id: "f1",
  code: "FAQ-101",
  question: "入籍前に申請できますか？",
  answerMarkdown: "できません。",
  category: { slug: "welfare", name: "福利厚生" },
  contactCode: "GA-GENERAL",
  ownerDepartment: { code: "GA", name: "総務部" },
  visibility: "INTERNAL",
  status: "DRAFT",
  links: [
    { targetType: "SECTION", targetId: "s5", programCode: null as never, documentCode: "REG-GA-001", articleNo: "第5条", label: "慶弔見舞金規程 第5条" },
    { targetType: "PROGRAM", targetId: "p1", programCode: "PRG-MARRIAGE-GIFT", documentCode: null as never, articleNo: null as never, label: "結婚祝金" },
  ],
  rowVersion: 3,
  ...over,
});

type Call = { url: string; method: string; headers: Headers; body: unknown };

/** 管理 API と、選択肢の取得（カテゴリ・窓口・制度・規程）の代わり */
function stubApi(handle: (call: Call) => Response | undefined) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(async (input, init) => {
      const call = {
        url: String(input),
        method: init?.method ?? "GET",
        headers: new Headers(init?.headers),
        body: init?.body ? JSON.parse(String(init.body)) : undefined,
      };
      calls.push(call);
      const res = handle(call);
      if (res) return res;
      if (call.url === "/api/v1/me") {
        return Response.json({ id: "u", email: "ito@example.com", displayName: "伊藤 誠", department: { code: "GA", name: "総務部" }, role: "CONTENT_EDITOR", employmentType: "REGULAR", joinedOn: "2026-04-01" });
      }
      if (call.url === "/api/v1/categories") return Response.json([{ slug: "welfare", name: "福利厚生", children: [] }]);
      if (call.url === "/api/v1/contacts") return Response.json([{ code: "GA", name: "総務部", contacts: [{ code: "GA-GENERAL", name: "総務部 庶務グループ" }] }]);
      if (call.url === "/api/v1/programs") return Response.json([{ id: "p1", code: "PRG-MARRIAGE-GIFT", name: "結婚祝金" }]);
      if (call.url === "/api/v1/documents") return Response.json([{ id: "d1", code: "REG-GA-001", title: "慶弔見舞金規程" }]);
      throw new Error(`unexpected ${call.method} ${call.url}`);
    }),
  );
  return calls;
}

describe("FAQ のフォームの値", () => {
  it("API の関連先（コード・条番号）をそのまま送り返せる", () => {
    const values = toFormValues(faq());
    expect(values.links).toEqual([
      { kind: "DOCUMENT", code: "REG-GA-001", articleNo: "第5条" },
      { kind: "PROGRAM", code: "PRG-MARRIAGE-GIFT", articleNo: "" },
    ]);
    expect(toContentRequest({ ...values, contactCode: "", links: [...values.links, { kind: "PROGRAM", code: "", articleNo: "" }] })).toEqual({
      question: "入籍前に申請できますか？",
      answerMarkdown: "できません。",
      categorySlug: "welfare",
      contactCode: undefined,
      visibility: "INTERNAL",
      links: [{ documentCode: "REG-GA-001", articleNo: "第5条" }, { programCode: "PRG-MARRIAGE-GIFT" }],
    });
  });
});

describe("EditFaq", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("版番号を If-Match に付けて保存し、次の保存では新しい版を使う", async () => {
    let version = 3;
    const calls = stubApi(({ url, method }) => {
      if (url === "/api/v1/admin/faqs/f1" && method === "GET") return Response.json(faq());
      if (url === "/api/v1/admin/faqs/f1" && method === "PUT") return Response.json(faq({ rowVersion: ++version }));
    });
    renderWithQuery(<EditFaq />);

    fireEvent.change(await screen.findByLabelText("質問"), { target: { value: "入籍前でも申請できますか？" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByRole("status")).toHaveTextContent("保存しました。");
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(calls.filter((c) => c.method === "PUT")).toHaveLength(2));

    const puts = calls.filter((c) => c.method === "PUT");
    expect(puts[0]?.headers.get("If-Match")).toBe('"3"');
    expect((puts[0]?.body as { question: string }).question).toBe("入籍前でも申請できますか？");
    expect(puts[1]?.headers.get("If-Match")).toBe('"4"');
  });

  it("公開するときは、先に保存してから公開する", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url === "/api/v1/admin/faqs/f1" && method === "GET") return Response.json(faq());
      if (url === "/api/v1/admin/faqs/f1" && method === "PUT") return Response.json(faq({ rowVersion: 4 }));
      if (url === "/api/v1/admin/faqs/f1/publish") return Response.json(faq({ rowVersion: 5, status: "PUBLISHED" }));
    });
    renderWithQuery(<EditFaq />);

    fireEvent.click(await screen.findByRole("button", { name: "保存して公開する" }));
    expect(await screen.findByRole("status")).toHaveTextContent("公開しました。");
    const writes = calls.filter((c) => c.method !== "GET");
    expect(writes.map((c) => `${c.method} ${c.url} ${c.headers.get("If-Match")}`)).toEqual([
      'PUT /api/v1/admin/faqs/f1 "3"',
      'POST /api/v1/admin/faqs/f1/publish "4"',
    ]);
    expect(screen.getByRole("button", { name: "公開を終了する" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "利用者向けの画面で見る" })).toHaveAttribute("href", "/faqs/f1");
  });

  it("ほかの人が先に更新していたら（412）、読み込み直すよう案内する", async () => {
    stubApi(({ url, method }) => {
      if (url === "/api/v1/admin/faqs/f1" && method === "GET") return Response.json(faq());
      if (method === "PUT") return Response.json({ detail: "版が一致しません" }, { status: 412 });
    });
    renderWithQuery(<EditFaq />);

    fireEvent.click(await screen.findByRole("button", { name: "保存" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("ほかの人が先に更新しました");
  });
});

describe("NewFaq", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    push.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("?question= で開くと、その質問を入れた状態になる", async () => {
    search = new URLSearchParams({ question: " 社員食堂のメニュー " });
    stubApi(() => undefined);
    renderWithQuery(<NewFaq />);

    expect(screen.getByLabelText("質問")).toHaveValue("社員食堂のメニュー");
    search = new URLSearchParams();
  });

  it("自分の部署を所管部署にして下書きを作り、編集画面へ移動する", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url === "/api/v1/admin/faqs" && method === "POST") return Response.json(faq({ id: "f9" }), { status: 201 });
    });
    renderWithQuery(<NewFaq />);

    await screen.findByText(/所管部署: 総務部/);
    fireEvent.change(screen.getByLabelText(/コード/), { target: { value: "faq-201" } });
    fireEvent.change(screen.getByLabelText("質問"), { target: { value: "社食はありますか？" } });
    fireEvent.change(screen.getByLabelText("回答（Markdown）"), { target: { value: "ありません。" } });
    await screen.findByRole("option", { name: "福利厚生" });
    fireEvent.change(screen.getByLabelText("カテゴリ"), { target: { value: "welfare" } });
    fireEvent.click(screen.getByRole("button", { name: "下書きとして保存" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/faqs/f9"));
    const post = calls.find((c) => c.method === "POST");
    expect(post?.body).toMatchObject({
      code: "FAQ-201",
      ownerDepartmentCode: "GA",
      content: { question: "社食はありますか？", categorySlug: "welfare", visibility: "INTERNAL", links: [] },
    });
  });
});
