import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Markdown } from "./markdown";

describe("Markdown", () => {
  it("番号付きの項と表を描画する", () => {
    const { container } = render(
      <Markdown text={"1. 結婚祝金を支給する。\n2. 申請する。\n\n| 対象 | 金額 |\n|---|---|\n| 本人 | 30,000円 |\n"} />,
    );
    expect(container.querySelectorAll("ol > li")).toHaveLength(2);
    expect(container.querySelector("table td")?.textContent).toBe("本人");
    expect(container.textContent).not.toContain("|---|");
  });

  it("検索語を mark で強調する（表の中も）", () => {
    const { container } = render(<Markdown text={"| 対象 | 金額 |\n|---|---|\n| 結婚 | 30,000円 |\n\n結婚したとき"} terms={["結婚"]} />);
    expect([...container.querySelectorAll("mark")].map((m) => m.textContent)).toEqual(["結婚", "結婚"]);
  });

  it("本文中の HTML と危険なリンクは描画しない", () => {
    const { container } = render(
      <Markdown text={'<img src=x onerror="alert(1)"> <b>太字</b>\n\n[リンク](javascript:alert(1))'} />,
    );
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("b")).toBeNull();
    expect(container.querySelector("a")?.getAttribute("href") ?? "").not.toContain("javascript");
  });
});
