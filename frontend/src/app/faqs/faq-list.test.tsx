import { fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { FaqList } from "./faq-list";

let query = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(query) }));

const faq = (id: string, question: string, category: string) => ({
  id,
  code: `FAQ-${id}`,
  question,
  category: { slug: category, name: category === "hr-leave" ? "休暇・休業" : "慶弔" },
});

function stubApi() {
  const fetch = vi.fn<typeof globalThis.fetch>(async (input) => {
    const url = String(input);
    if (url === "/api/v1/categories")
      return Response.json([
        { slug: "hr", name: "人事・労務", children: [{ slug: "hr-leave", name: "休暇・休業", children: [] }] },
        { slug: "welfare", name: "福利厚生", children: [] },
      ]);
    if (url.startsWith("/api/v1/faqs"))
      return Response.json(
        url.includes("category=hr")
          ? [faq("1", "結婚休暇は何日ありますか？", "hr-leave")]
          : [faq("1", "結婚休暇は何日ありますか？", "hr-leave"), faq("2", "結婚祝金はいくらですか？", "welfare-ceremony")],
      );
    throw new Error(`unexpected ${url}`);
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("FaqList", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("カテゴリごとに見出しを付けて並べ、質問の文で絞り込める", async () => {
    query = "";
    stubApi();
    renderWithQuery(<FaqList />);

    const leave = await screen.findByRole("region", { name: "休暇・休業" });
    expect(within(leave).getByRole("link", { name: "結婚休暇は何日ありますか？" })).toHaveAttribute("href", "/faqs/1");
    expect(screen.getByRole("status")).toHaveTextContent("質問 2 件");

    fireEvent.change(screen.getByLabelText("質問の文で絞り込む"), { target: { value: "祝金" } });
    expect(screen.getByRole("status")).toHaveTextContent("質問 1 件");
    expect(screen.queryByRole("region", { name: "休暇・休業" })).toBeNull();

    fireEvent.change(screen.getByLabelText("質問の文で絞り込む"), { target: { value: "退職金" } });
    expect(screen.getByText(/該当する質問はありません。/)).toBeInTheDocument();
  });

  it("?category= の親カテゴリで API を絞り込み、選んでいるカテゴリを示す", async () => {
    query = "category=hr";
    const fetch = stubApi();
    renderWithQuery(<FaqList />);

    expect(await screen.findByText("「人事・労務」の質問 1 件")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "人事・労務" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "すべて" })).not.toHaveAttribute("aria-current");
    expect(fetch.mock.calls.some(([u]) => String(u) === "/api/v1/faqs?category=hr")).toBe(true);
  });
});
