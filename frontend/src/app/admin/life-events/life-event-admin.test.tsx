import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { EditLifeEvent } from "./[code]/edit-life-event";
import { type AdminLifeEvent, EMPTY_LIFE_EVENT, toRequest } from "./life-event-api";
import { NewLifeEvent } from "./new/new-life-event";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useParams: () => ({ code: "MARRIAGE" }),
  useRouter: () => ({ push }),
}));

const marriage = (over: Partial<AdminLifeEvent> = {}): AdminLifeEvent => ({
  id: "e1",
  code: "MARRIAGE",
  name: "結婚",
  icon: "ring",
  description: "結婚したとき",
  sortOrder: 0,
  programs: [
    { code: "PRG-MARRIAGE-GIFT", name: "結婚祝金", relation: "PRIMARY" },
    { code: "PRG-MARRIAGE-LEAVE", name: "結婚休暇", relation: "PRIMARY" },
  ],
  rowVersion: 1,
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
      if (call.url === "/api/v1/programs") {
        return Response.json([
          { id: "p1", code: "PRG-MARRIAGE-GIFT", name: "結婚祝金" },
          { id: "p2", code: "PRG-MARRIAGE-LEAVE", name: "結婚休暇" },
        ]);
      }
      throw new Error(`unexpected ${call.method} ${call.url}`);
    }),
  );
  return calls;
}

describe("きっかけのフォームの値", () => {
  it("空の行と空の任意項目は送らない", () => {
    expect(
      toRequest({
        ...EMPTY_LIFE_EVENT,
        name: " 海外赴任 ",
        programs: [
          { programCode: "PRG-BUSINESS-TRIP", relation: "PRIMARY" },
          { programCode: "", relation: "RELATED" },
        ],
      }),
    ).toEqual({
      name: "海外赴任",
      icon: undefined,
      description: undefined,
      sortOrder: 100,
      programs: [{ programCode: "PRG-BUSINESS-TRIP", relation: "PRIMARY" }],
    });
  });
});

describe("EditLifeEvent", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("制度の並びを変えて、版番号を付けて保存する", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url === "/api/v1/admin/life-events/MARRIAGE" && method === "GET") return Response.json(marriage());
      if (method === "PUT") return Response.json(marriage({ rowVersion: 2 }));
    });
    renderWithQuery(<EditLifeEvent />);

    await screen.findAllByRole("option", { name: "結婚休暇（PRG-MARRIAGE-LEAVE）" });
    fireEvent.click(screen.getByRole("button", { name: "制度 2 を上へ" }));
    fireEvent.change(screen.getByLabelText("制度 2 の種類"), { target: { value: "RELATED" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByRole("status")).toHaveTextContent("保存しました。");
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.ifMatch).toBe('"1"');
    expect((put?.body as { programs: unknown }).programs).toEqual([
      { programCode: "PRG-MARRIAGE-LEAVE", relation: "PRIMARY" },
      { programCode: "PRG-MARRIAGE-GIFT", relation: "RELATED" },
    ]);
  });

  it("入力の誤りは backend の説明を表示する", async () => {
    stubApi(({ url, method }) => {
      if (url === "/api/v1/admin/life-events/MARRIAGE" && method === "GET") return Response.json(marriage());
      if (method === "PUT") {
        return Response.json({ detail: "「まず確認する制度」を1つ以上選んでください。" }, { status: 400 });
      }
    });
    renderWithQuery(<EditLifeEvent />);

    fireEvent.click(await screen.findByRole("button", { name: "保存" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("「まず確認する制度」を1つ以上選んでください。");
  });
});

describe("NewLifeEvent", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    push.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("作成して編集画面へ移動する（最初の制度は「まず確認する制度」）", async () => {
    const calls = stubApi(({ method }) => {
      if (method === "POST") return Response.json(marriage({ code: "OVERSEAS", name: "海外赴任" }), { status: 201 });
    });
    renderWithQuery(<NewLifeEvent />);

    fireEvent.change(screen.getByLabelText(/コード/), { target: { value: "overseas" } });
    fireEvent.change(screen.getByLabelText("名前"), { target: { value: "海外赴任" } });
    fireEvent.click(screen.getByRole("button", { name: "制度を追加" }));
    await screen.findByRole("option", { name: "結婚祝金（PRG-MARRIAGE-GIFT）" });
    fireEvent.change(screen.getByLabelText("制度 1"), { target: { value: "PRG-MARRIAGE-GIFT" } });
    fireEvent.click(screen.getByRole("button", { name: "作成" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin/life-events/OVERSEAS"));
    expect(calls.find((c) => c.method === "POST")?.body).toMatchObject({
      code: "OVERSEAS",
      content: { name: "海外赴任", programs: [{ programCode: "PRG-MARRIAGE-GIFT", relation: "PRIMARY" }] },
    });
  });
});
