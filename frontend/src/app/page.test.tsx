import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import HomePage from "./page";

describe("HomePage", () => {
  it("タイトルと架空データの注記を表示する", () => {
    render(<HomePage />);
    expect(screen.getByRole("heading", { level: 1, name: "社内規程ナビ" })).toBeInTheDocument();
    expect(screen.getByText(/架空企業「架空商事株式会社」/)).toBeInTheDocument();
  });
});
