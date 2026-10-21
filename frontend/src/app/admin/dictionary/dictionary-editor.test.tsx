import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { DictionaryEditor } from "./dictionary-editor";

const keyword = (over: Record<string, unknown> = {}) => ({
  id: "k1",
  term: "入籍",
  normalizedTerm: "入籍",
  targetType: "LIFE_EVENT",
  targetId: "e1",
  targetLabel: "結婚",
  weight: 1.5,
  ...over,
});

/** URL とメソッドに応じて応答を返す fetch の代わり */
function routes(handler: (url: string, method: string, body: unknown) => Response | undefined) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? "GET";
    const res = handler(url, method, init?.body ? JSON.parse(String(init.body)) : undefined);
    if (!res) throw new Error(`unexpected ${method} ${url}`);
    return res;
  });
}

describe("DictionaryEditor", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("言葉から案内先を確認し、検索へのリンクを出す", async () => {
    vi.stubGlobal(
      "fetch",
      routes((url) => {
        if (url.includes("/targets")) return Response.json([]);
        if (url.includes("term=")) return Response.json([keyword(), keyword({ id: "k2", targetType: "PROGRAM", targetLabel: null })]);
      }),
    );
    renderWithQuery(<DictionaryEditor />);

    fireEvent.change(screen.getByLabelText("確認する言葉"), { target: { value: " 入籍 " } });
    fireEvent.click(screen.getByRole("button", { name: "確認" }));

    const table = await screen.findByRole("table");
    expect(within(table).getByText("結婚")).toBeInTheDocument();
    expect(within(table).getByText("（閲覧権限のない案内先）")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "「入籍」で検索してみる" })).toHaveAttribute(
      "href",
      `/search?q=${encodeURIComponent("入籍")}`,
    );
  });

  it("案内先を選んで言葉を追加すると、一覧を読み直す", async () => {
    let saved = false;
    const fetchMock = routes((url, method, body) => {
      if (url.endsWith("/targets?targetType=LIFE_EVENT")) return Response.json([{ id: "e1", code: "MARRIAGE", label: "結婚" }]);
      if (method === "POST") {
        expect(body).toEqual({ term: "ご祝儀", targetType: "LIFE_EVENT", targetId: "e1", weight: 1 });
        saved = true;
        return Response.json(keyword({ id: "k3", term: "ご祝儀" }), { status: 201 });
      }
      if (url.includes("targetId=e1")) return Response.json(saved ? [keyword(), keyword({ id: "k3", term: "ご祝儀" })] : [keyword()]);
    });
    vi.stubGlobal("fetch", fetchMock);
    renderWithQuery(<DictionaryEditor />);

    await screen.findByRole("option", { name: "結婚（MARRIAGE）" });
    fireEvent.change(screen.getByLabelText("案内先"), { target: { value: "e1" } });
    expect(await screen.findByText("入籍")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("追加する言葉"), { target: { value: "ご祝儀" } });
    fireEvent.click(screen.getByRole("button", { name: "追加" }));

    expect(await screen.findByText("ご祝儀")).toBeInTheDocument();
    expect(screen.getByLabelText("追加する言葉")).toHaveValue("");
  });

  it("重複などのエラーは backend の説明を表示する", async () => {
    vi.stubGlobal(
      "fetch",
      routes((url, method) => {
        if (url.includes("/targets")) return Response.json([{ id: "e1", code: "MARRIAGE", label: "結婚" }]);
        if (method === "POST") return Response.json({ detail: "同じキーワードが既に登録されています。" }, { status: 409 });
        if (url.includes("targetId=e1")) return Response.json([]);
      }),
    );
    renderWithQuery(<DictionaryEditor />);

    await screen.findByRole("option", { name: "結婚（MARRIAGE）" });
    fireEvent.change(screen.getByLabelText("案内先"), { target: { value: "e1" } });
    fireEvent.change(screen.getByLabelText("追加する言葉"), { target: { value: "入籍" } });
    fireEvent.click(screen.getByRole("button", { name: "追加" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("同じキーワードが既に登録されています。");
  });

  it("確認してから削除する", async () => {
    const fetchMock = routes((url, method) => {
      if (url.includes("/targets")) return Response.json([]);
      if (method === "DELETE") return new Response(null, { status: 204 });
      if (url.includes("term=")) return Response.json([keyword()]);
    });
    vi.stubGlobal("fetch", fetchMock);
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderWithQuery(<DictionaryEditor />);

    fireEvent.change(screen.getByLabelText("確認する言葉"), { target: { value: "入籍" } });
    fireEvent.click(screen.getByRole("button", { name: "確認" }));
    const button = await screen.findByRole("button", { name: "削除" });

    fireEvent.click(button);
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(false);

    fireEvent.click(button);
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url, init]) => init?.method === "DELETE" && String(url).endsWith("/k1"))).toBe(true),
    );
    expect(confirm).toHaveBeenCalledWith("「入籍」を辞書から削除しますか？");
  });
});
