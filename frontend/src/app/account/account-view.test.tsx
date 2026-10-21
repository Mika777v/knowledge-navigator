import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { AccountView } from "./account-view";

const me = {
  id: "u",
  email: "sato@example.com",
  displayName: "佐藤 太郎",
  department: { code: "SALES-EAST", name: "東日本営業課" },
  role: "EMPLOYEE",
  employmentType: "REGULAR",
  joinedOn: "2023-04-01",
};

function stubApi(onChange: (body: unknown) => Response) {
  const fetch = vi.fn<typeof globalThis.fetch>(async (input, init) => {
    if (String(input) === "/api/v1/me") return Response.json(me);
    if (String(input) === "/api/v1/me/password") return onChange(JSON.parse(String(init?.body)));
    throw new Error(`unexpected ${String(input)}`);
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function fill(current: string, next: string, confirm = next) {
  fireEvent.change(screen.getByLabelText("今のパスワード"), { target: { value: current } });
  fireEvent.change(screen.getByLabelText("新しいパスワード（8文字以上）"), { target: { value: next } });
  fireEvent.change(screen.getByLabelText("新しいパスワード（確認）"), { target: { value: confirm } });
  fireEvent.click(screen.getByRole("button", { name: "変更する" }));
}

describe("AccountView", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("所属・ロールを表示し、パスワードを変更できる", async () => {
    const fetch = stubApi(() => new Response(null, { status: 204 }));
    renderWithQuery(<AccountView />);
    expect(await screen.findByText("東日本営業課")).toBeInTheDocument();
    expect(screen.getByText("一般社員")).toBeInTheDocument();

    fill("old-password", "new-password-1");

    expect(await screen.findByRole("status")).toHaveTextContent("パスワードを変更しました。");
    const call = fetch.mock.calls.find(([url]) => String(url) === "/api/v1/me/password");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ currentPassword: "old-password", newPassword: "new-password-1" });
    expect(screen.getByLabelText("今のパスワード")).toHaveValue("");
  });

  it("確認が一致しない・短いときは送らない", async () => {
    const fetch = stubApi(() => new Response(null, { status: 204 }));
    renderWithQuery(<AccountView />);
    await screen.findByText("東日本営業課");

    fill("old-password", "new-password-1", "new-password-2");
    expect(screen.getByRole("alert")).toHaveTextContent("新しいパスワード（確認）が一致しません。");
    fill("old-password", "short");
    expect(screen.getByRole("alert")).toHaveTextContent("8文字以上");
    expect(fetch.mock.calls.some(([url]) => String(url) === "/api/v1/me/password")).toBe(false);
  });

  it("今のパスワードが違うときは、backend の説明を出す（ログイン画面へは移動しない）", async () => {
    stubApi(() => Response.json({ status: 400, detail: "今のパスワードが正しくありません。" }, { status: 400 }));
    renderWithQuery(<AccountView />);
    await screen.findByText("東日本営業課");

    fill("wrong-password", "new-password-1");

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("今のパスワードが正しくありません。"));
  });
});
