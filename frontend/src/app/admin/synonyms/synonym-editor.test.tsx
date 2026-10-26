import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { SynonymEditor, splitTerms } from "./synonym-editor";

let search = new URLSearchParams();
vi.mock("next/navigation", () => ({ useSearchParams: () => search }));

const group = (over: Record<string, unknown> = {}) => ({
  id: "g1",
  canonical: "育児休業",
  terms: ["育児休業", "育休", "育児休暇"],
  rowVersion: 2,
  ...over,
});

type Call = { url: string; method: string; ifMatch: string | null; body: unknown };

function routes(handler: (call: Call) => Response | undefined) {
  const calls: Call[] = [];
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const call = {
      url: String(input),
      method: init?.method ?? "GET",
      ifMatch: new Headers(init?.headers).get("If-Match"),
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
    };
    calls.push(call);
    const res = handler(call);
    if (!res) throw new Error(`unexpected ${call.method} ${call.url}`);
    return res;
  });
  return { fetchMock, calls };
}

describe("splitTerms", () => {
  it("読点・カンマ・改行で区切り、空白と空の語を除く", () => {
    expect(splitTerms(" 育休、育児休暇,  ,産休，\n時短 ")).toEqual(["育休", "育児休暇", "産休", "時短"]);
  });
});

describe("SynonymEditor", () => {
  beforeEach(() => {
    search = new URLSearchParams();
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("グループの一覧を、代表の言葉と同じ意味の言葉に分けて出す", async () => {
    const { fetchMock } = routes(({ method }) => (method === "GET" ? Response.json([group()]) : undefined));
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<SynonymEditor />);

    const terms = await screen.findByRole("list", { name: "「育児休業」と同じ意味の言葉" });
    expect(within(terms).getAllByRole("listitem").map((li) => li.textContent)).toEqual(["育休", "育児休暇"]);
  });

  it("?term= で開くと、その言葉を「同じ意味の言葉」に入れた状態になる", async () => {
    search = new URLSearchParams({ term: "社食" });
    const { fetchMock } = routes(({ method }) => (method === "GET" ? Response.json([]) : undefined));
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<SynonymEditor />);

    expect(screen.getByLabelText("同じ意味の言葉（「、」で区切る）")).toHaveValue("社食");
    expect(screen.getByText(/「社食」を同じ意味の言葉に入れました/)).toBeInTheDocument();
  });

  it("言葉で絞り込む", async () => {
    const { fetchMock, calls } = routes(({ method }) => (method === "GET" ? Response.json([]) : undefined));
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<SynonymEditor />);

    fireEvent.change(screen.getByLabelText("言葉で絞り込む"), { target: { value: " 育休 " } });
    fireEvent.click(screen.getByRole("button", { name: "絞り込む" }));

    expect(await screen.findByText("「育休」を含むグループはありません。")).toBeInTheDocument();
    expect(calls.at(-1)?.url).toBe(`/api/v1/admin/synonyms?q=${encodeURIComponent("育休")}`);
  });

  it("追加すると、区切った言葉を送る。ほかのグループと重なると理由を出す", async () => {
    const { fetchMock, calls } = routes(({ method }) => {
      if (method === "GET") return Response.json([]);
      if (method === "POST")
        return Response.json(
          { status: 409, detail: "「育休」は類義語「育児休業」のグループに登録されています。" },
          { status: 409, headers: { "Content-Type": "application/problem+json" } },
        );
    });
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<SynonymEditor />);

    fireEvent.change(screen.getByLabelText("代表の言葉"), { target: { value: "育児休暇" } });
    fireEvent.change(screen.getByLabelText("同じ意味の言葉（「、」で区切る）"), { target: { value: "育休、 子育て休暇" } });
    fireEvent.click(screen.getByRole("button", { name: "追加" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("「育休」は類義語「育児休業」のグループに登録されています。");
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ canonical: "育児休暇", terms: ["育休", "子育て休暇"] });
  });

  it("編集は版番号を If-Match に付けて保存し、先に更新されていたら読み込み直しを促す", async () => {
    const { fetchMock, calls } = routes(({ method }) => {
      if (method === "GET") return Response.json([group()]);
      if (method === "PUT")
        return Response.json({ status: 412, detail: "x" }, { status: 412, headers: { "Content-Type": "application/problem+json" } });
    });
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<SynonymEditor />);

    fireEvent.click(await screen.findByRole("button", { name: "編集" }));
    const terms = screen.getAllByLabelText("同じ意味の言葉（「、」で区切る）")[1];
    expect(terms).toHaveValue("育休、育児休暇");
    fireEvent.change(terms, { target: { value: "育休" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("ほかの人が先に更新しました");
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.ifMatch).toBe('"2"');
    expect(put?.url).toBe("/api/v1/admin/synonyms/g1");
    expect(put?.body).toEqual({ canonical: "育児休業", terms: ["育休"] });
  });

  it("削除は確認してから行う", async () => {
    const { fetchMock, calls } = routes(({ method }) => {
      if (method === "GET") return Response.json([group()]);
      if (method === "DELETE") return new Response(null, { status: 204 });
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("confirm", vi.fn(() => true));
    renderWithQuery(<SynonymEditor />);

    fireEvent.click(await screen.findByRole("button", { name: "削除" }));
    await waitFor(() => expect(calls.some((c) => c.method === "DELETE" && c.url === "/api/v1/admin/synonyms/g1")).toBe(true));
  });
});
