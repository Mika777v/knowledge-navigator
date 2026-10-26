import { act, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { SearchForm } from "./search-form";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const suggestions = [
  { type: "LIFE_EVENT", id: "e1", code: "MARRIAGE", label: "結婚" },
  { type: "PROGRAM", id: "p1", code: "PRG-MARRIAGE-GIFT", label: "結婚祝金" },
];

describe("SearchForm", () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockImplementation(async () => Response.json(suggestions));
  });
  afterEach(() => {
    fetchMock.mockReset();
    push.mockReset();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  async function type(text: string) {
    fireEvent.change(screen.getByRole("combobox", { name: "知りたいこと" }), { target: { value: text } });
    await act(async () => {
      vi.advanceTimersByTime(250);
    });
  }

  it("打ち終わってから候補を探し、きっかけ・制度の候補を出す", async () => {
    renderWithQuery(<SearchForm />);
    await type("結婚");

    const options = await screen.findAllByRole("option");
    expect(options.map((o) => o.textContent)).toEqual(["きっかけ結婚", "制度結婚祝金"]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toBe(`/api/v1/search/suggest?q=${encodeURIComponent("結婚")}`);
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-expanded", "true");
  });

  it("↓ と Enter で選ぶ。制度は詳細を開き、きっかけはその名前で検索する", async () => {
    renderWithQuery(<SearchForm />);
    await type("結婚");
    await screen.findAllByRole("option");
    const input = screen.getByRole("combobox");

    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    expect(screen.getAllByRole("option")[1]).toHaveAttribute("aria-selected", "true");
    fireEvent.keyDown(input, { key: "Enter" });
    expect(push).toHaveBeenCalledWith("/programs/p1");

    await type("けっ");
    fireEvent.click((await screen.findAllByRole("option"))[0]);
    expect(push).toHaveBeenLastCalledWith(`/search?q=${encodeURIComponent("結婚")}`);
  });

  it("候補を選ばずに Enter なら、入力そのままで検索する。Esc で候補を閉じる", async () => {
    renderWithQuery(<SearchForm />);
    await type("結婚したら");
    await screen.findAllByRole("option");
    const input = screen.getByRole("combobox");

    fireEvent.keyDown(input, { key: "Escape" });
    expect(screen.queryAllByRole("option")).toHaveLength(0);
    fireEvent.submit(input.closest("form")!);
    expect(push).toHaveBeenCalledWith(`/search?q=${encodeURIComponent("結婚したら")}`);
  });

  it("変換中（IME）の Enter では候補を選ばない", async () => {
    renderWithQuery(<SearchForm />);
    await type("結婚");
    await screen.findAllByRole("option");
    const input = screen.getByRole("combobox");

    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "Enter", isComposing: true });
    expect(push).not.toHaveBeenCalled();
  });
});
