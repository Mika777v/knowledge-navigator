import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Highlight } from "./highlight";

describe("Highlight", () => {
  it("検索語を mark で囲む（大文字・小文字を区別しない）", () => {
    const { container } = render(<Highlight text="PC が壊れたら pc 窓口へ" terms={["pc"]} />);
    expect([...container.querySelectorAll("mark")].map((m) => m.textContent)).toEqual(["PC", "pc"]);
  });

  it("正規表現の記号を含む語もそのまま探す", () => {
    const { container } = render(<Highlight text="金額は(1)のとおり" terms={["(1)"]} />);
    expect(container.querySelector("mark")?.textContent).toBe("(1)");
  });

  it("本文の HTML は解釈せずに文字として表示する", () => {
    const { container } = render(<Highlight text="<b>太字</b>" terms={["太字"]} />);
    expect(container.querySelector("b")).toBeNull();
    expect(container.textContent).toBe("<b>太字</b>");
  });
});
