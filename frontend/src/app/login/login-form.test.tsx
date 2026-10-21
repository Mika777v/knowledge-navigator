import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import { LoginForm } from "./login-form";

const replace = vi.fn();
let next: string | null = null;
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => new URLSearchParams(next ? { next } : {}),
}));

const loginMock = vi.fn();
vi.mock("@/lib/auth/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/auth/api")>()),
  login: (...args: unknown[]) => loginMock(...args),
}));

function renderForm() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <LoginForm />
    </QueryClientProvider>,
  );
  fireEvent.change(screen.getByLabelText("メールアドレス"), { target: { value: "sato@example.com" } });
  fireEvent.change(screen.getByLabelText("パスワード"), { target: { value: "pw" } });
  fireEvent.click(screen.getByRole("button", { name: "ログイン" }));
}

describe("LoginForm", () => {
  beforeEach(() => {
    replace.mockReset();
    loginMock.mockReset();
    next = null;
  });

  it("ログインできたら next のページへ移動する", async () => {
    next = "/documents/abc";
    loginMock.mockResolvedValueOnce({ displayName: "佐藤 太郎" });
    renderForm();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/documents/abc"));
    expect(loginMock).toHaveBeenCalledWith("sato@example.com", "pw");
  });

  it("next が外部サイトならホームへ移動する", async () => {
    next = "https://evil.example";
    loginMock.mockResolvedValueOnce({});
    renderForm();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
  });

  it("認証に失敗したらエラーを表示して移動しない", async () => {
    loginMock.mockRejectedValueOnce(new ApiError(401, "Unauthorized"));
    renderForm();
    expect(await screen.findByRole("alert")).toHaveTextContent("メールアドレスまたはパスワードが正しくありません。");
    expect(replace).not.toHaveBeenCalled();
  });
});
