import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { EditProgram } from "./[id]/edit-program";
import { NewProgram } from "./new/new-program";
import { type AdminProgram, toAttributes, toFormValues } from "./program-api";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "p1" }),
  useRouter: () => ({ push }),
}));

const program = (over: Partial<AdminProgram> = {}): AdminProgram => ({
  id: "p1",
  code: "PRG-MARRIAGE-GIFT",
  name: "結婚祝金",
  nameKana: "けっこんいわいきん",
  summary: "社員が結婚したときに支給する。",
  eligibility: "勤続6か月以上",
  benefit: "30,000円",
  howToApply: "総務部に申請する",
  deadlineNote: "入籍後6か月以内",
  requiredDocuments: null as never,
  category: { slug: "welfare", name: "福利厚生" },
  ownerDepartment: { code: "GA", name: "総務部" },
  visibility: "INTERNAL",
  status: "DRAFT",
  basis: [{ documentCode: "REG-GA-001", documentTitle: "慶弔見舞金規程", articleNo: "第5条", sectionId: "s5", heading: "結婚祝金" }],
  contacts: [{ code: "GA-GENERAL", name: "総務部 庶務グループ", role: "APPLY" }],
  rowVersion: 2,
  ...over,
});

type Call = { url: string; method: string; ifMatch: string | null; body: unknown };

function stubApi(handle: (call: Call) => Response | undefined) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(async (input, init) => {
      const call = {
        url: String(input),
        method: init?.method ?? "GET",
        ifMatch: new Headers(init?.headers).get("If-Match"),
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
      if (call.url === "/api/v1/programs") return Response.json([]);
      if (call.url === "/api/v1/documents") return Response.json([{ id: "d1", code: "REG-GA-001", title: "慶弔見舞金規程" }]);
      if (call.url === "/api/v1/documents/d1") {
        return Response.json({ sections: [{ id: "s5", articleNo: "第5条", heading: "結婚祝金" }, { id: "s6", articleNo: "第6条", heading: "出産祝金" }] });
      }
      throw new Error(`unexpected ${call.method} ${call.url}`);
    }),
  );
  return calls;
}

/** 保存の API（説明 → 根拠 → 窓口）を、呼ばれるたびに版番号を1つ上げて返す */
function saveHandler(start: number, extra?: (call: Call) => Response | undefined) {
  let version = start;
  return (call: Call) => {
    if (call.url === "/api/v1/admin/programs/p1" && call.method === "GET") return Response.json(program());
    if (call.method === "PUT") return Response.json(program({ rowVersion: ++version }));
    return extra?.(call);
  };
}

describe("制度のフォームの値", () => {
  it("根拠・窓口をコードで持ち、空の項目は送らない", () => {
    const values = toFormValues(program());
    expect(values.basis).toEqual([{ documentCode: "REG-GA-001", articleNo: "第5条" }]);
    expect(values.contacts).toEqual([{ contactCode: "GA-GENERAL", role: "APPLY" }]);
    expect(toAttributes({ ...values, requiredDocuments: "  " })).toMatchObject({
      name: "結婚祝金",
      requiredDocuments: undefined,
      categorySlug: "welfare",
    });
  });
});

describe("EditProgram", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("説明 → 根拠 → 窓口の順に、前の応答の版番号を使って保存する", async () => {
    const calls = stubApi(saveHandler(2));
    renderWithQuery(<EditProgram />);

    fireEvent.change(await screen.findByLabelText("内容（金額・日数など）"), { target: { value: "50,000円" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByRole("status")).toHaveTextContent("保存しました。");
    const puts = calls.filter((c) => c.method === "PUT");
    expect(puts.map((c) => `${c.url} ${c.ifMatch}`)).toEqual([
      '/api/v1/admin/programs/p1 "2"',
      '/api/v1/admin/programs/p1/basis "3"',
      '/api/v1/admin/programs/p1/contacts "4"',
    ]);
    expect((puts[0]?.body as { benefit: string }).benefit).toBe("50,000円");
    expect(puts[1]?.body).toEqual({ basis: [{ documentCode: "REG-GA-001", articleNo: "第5条" }] });
    expect(puts[2]?.body).toEqual({ contacts: [{ contactCode: "GA-GENERAL", role: "APPLY" }] });
  });

  it("根拠の条を、規程の公開版の条から選べる", async () => {
    const calls = stubApi(saveHandler(2));
    renderWithQuery(<EditProgram />);

    await screen.findByRole("option", { name: "第6条（出産祝金）" });
    fireEvent.change(screen.getByLabelText("根拠 1 の条"), { target: { value: "第6条" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await screen.findByRole("status");
    expect(calls.find((c) => c.url.endsWith("/basis"))?.body).toEqual({
      basis: [{ documentCode: "REG-GA-001", articleNo: "第6条" }],
    });
  });

  it("公開するときは、保存してから最新の版で公開する", async () => {
    const calls = stubApi(
      saveHandler(2, (call) =>
        call.url.endsWith("/publish") ? Response.json(program({ rowVersion: 6, status: "PUBLISHED" })) : undefined,
      ),
    );
    renderWithQuery(<EditProgram />);

    fireEvent.click(await screen.findByRole("button", { name: "保存して公開する" }));
    expect(await screen.findByRole("status")).toHaveTextContent("公開しました。");
    expect(calls.find((c) => c.url.endsWith("/publish"))?.ifMatch).toBe('"5"');
    expect(screen.getByRole("button", { name: "公開を終了する" })).toBeInTheDocument();
  });

  it("公開できない理由は backend の説明を表示する", async () => {
    stubApi(
      saveHandler(2, (call) =>
        call.url.endsWith("/publish")
          ? Response.json({ detail: "窓口を1件以上登録してください。" }, { status: 409 })
          : undefined,
      ),
    );
    renderWithQuery(<EditProgram />);

    fireEvent.click(await screen.findByRole("button", { name: "保存して公開する" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("窓口を1件以上登録してください。");
  });
});

describe("NewProgram", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    push.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("作成してから根拠・窓口を登録し、編集画面へ移動する", async () => {
    let version = 0;
    const calls = stubApi((call) => {
      if (call.url === "/api/v1/admin/programs" && call.method === "POST") {
        return Response.json(program({ id: "p9", rowVersion: 0, basis: [], contacts: [] }), { status: 201 });
      }
      if (call.method === "PUT") return Response.json(program({ id: "p9", rowVersion: ++version }));
    });
    renderWithQuery(<NewProgram />);

    await screen.findByText(/所管部署: 総務部/);
    fireEvent.change(screen.getByLabelText(/コード/), { target: { value: "prg-new" } });
    fireEvent.change(screen.getByLabelText("制度名"), { target: { value: "新しい制度" } });
    fireEvent.change(screen.getByLabelText("概要"), { target: { value: "説明" } });
    fireEvent.click(screen.getByRole("button", { name: "窓口を追加" }));
    await screen.findByRole("option", { name: "総務部 庶務グループ" });
    fireEvent.change(screen.getByLabelText("窓口 1"), { target: { value: "GA-GENERAL" } });
    fireEvent.click(screen.getByRole("button", { name: "下書きとして保存" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/programs/p9"));
    expect(calls.find((c) => c.method === "POST")?.body).toMatchObject({
      code: "PRG-NEW",
      ownerDepartmentCode: "GA",
      attributes: { name: "新しい制度", summary: "説明" },
    });
    expect(calls.filter((c) => c.method === "PUT").map((c) => `${c.url} ${c.ifMatch}`)).toEqual([
      '/api/v1/admin/programs/p9/basis "0"',
      '/api/v1/admin/programs/p9/contacts "1"',
    ]);
  });
});
