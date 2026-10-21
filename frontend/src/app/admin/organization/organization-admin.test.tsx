import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { OrganizationAdmin } from "./organization-admin";

let tab = "departments";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams({ tab }) }));

const departments = [
  { id: "d1", code: "HR", name: "人事部", description: "", parentCode: "", sortOrder: 10, active: true, rowVersion: 2 },
  { id: "d2", code: "GA", name: "総務部", description: "", parentCode: "", sortOrder: 20, active: true, rowVersion: 0 },
];
const contacts = [
  {
    id: "c1",
    code: "HR-WELFARE",
    departmentCode: "HR",
    name: "人事部 福利厚生担当",
    description: "",
    email: "welfare@example.com",
    phoneExt: "1234",
    chatUrl: "",
    officeHours: "",
    primary: true,
    sortOrder: 0,
    rowVersion: 1,
  },
];
const users = [
  {
    id: "u1",
    email: "sato@example.com",
    displayName: "佐藤 太郎",
    departmentCode: "HR",
    role: "EMPLOYEE",
    employmentType: "REGULAR",
    joinedOn: "2023-04-01",
    active: true,
    rowVersion: 3,
  },
];

type Call = { url: string; method: string; ifMatch: string | null; body: unknown };

function stubApi(handle: (call: Call) => Response | undefined = () => undefined) {
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
      if (call.method === "GET" && call.url === "/api/v1/admin/departments") return Response.json(departments);
      if (call.method === "GET" && call.url === "/api/v1/admin/contacts") return Response.json(contacts);
      if (call.method === "GET" && call.url === "/api/v1/admin/users") return Response.json(users);
      throw new Error(`unexpected ${call.method} ${call.url}`);
    }),
  );
  return calls;
}

describe("OrganizationAdmin", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("部署を編集すると、読み込んだときの版を付けて送る", async () => {
    tab = "departments";
    const calls = stubApi((c) =>
      c.method === "PUT" ? Response.json({ ...departments[0], name: "人事総務部", rowVersion: 3 }) : undefined,
    );
    renderWithQuery(<OrganizationAdmin />);
    fireEvent.click(await screen.findByRole("button", { name: "人事部を編集" }));
    expect(screen.getByLabelText("コード")).toBeDisabled();
    fireEvent.change(screen.getByLabelText("名前"), { target: { value: " 人事総務部 " } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByRole("status")).toHaveTextContent("人事総務部を保存しました。");
    const put = calls.find((c) => c.method === "PUT");
    expect(put).toMatchObject({ url: "/api/v1/admin/departments/HR", ifMatch: '"2"' });
    expect(put?.body).toEqual({ name: "人事総務部", sortOrder: 10, active: true });
  });

  it("窓口を追加すると、空の任意項目は送らない", async () => {
    tab = "contacts";
    const calls = stubApi((c) =>
      c.method === "POST" ? Response.json({ ...contacts[0], code: "GA-DESK", name: "総務窓口" }) : undefined,
    );
    renderWithQuery(<OrganizationAdmin />);
    expect(await screen.findByText("代表窓口")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "窓口を追加" }));
    fireEvent.change(screen.getByLabelText("コード"), { target: { value: "ga-desk" } });
    fireEvent.change(screen.getByLabelText("名前"), { target: { value: "総務窓口" } });
    fireEvent.change(screen.getByLabelText("部署"), { target: { value: "GA" } });
    fireEvent.change(screen.getByLabelText("内線"), { target: { value: "2000" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({
      code: "GA-DESK",
      content: { departmentCode: "GA", name: "総務窓口", phoneExt: "2000", primary: false, sortOrder: 0 },
    });
  });

  it("ユーザーの更新がほかの人と重なったら、読み込み直すよう案内する", async () => {
    tab = "users";
    stubApi((c) =>
      c.method === "PUT"
        ? Response.json({ status: 412, detail: "版が一致しません。" }, { status: 412 })
        : undefined,
    );
    renderWithQuery(<OrganizationAdmin />);
    fireEvent.click(await screen.findByRole("button", { name: "佐藤 太郎を編集" }));
    expect(screen.queryByLabelText("初期パスワード（8文字以上）")).toBeNull();
    fireEvent.change(screen.getByLabelText("ロール"), { target: { value: "MANAGER" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("ほかの人が先に更新しました。");
  });
});
