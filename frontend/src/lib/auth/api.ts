import { apiFetch } from "@/lib/api/http";

/** GET /api/v1/me の応答（backend の MeResponse と同じ形） */
export type Me = {
  id: string;
  email: string;
  displayName: string;
  department: { code: string; name: string } | null;
  role: "EMPLOYEE" | "MANAGER" | "CONTENT_EDITOR" | "SYSTEM_ADMIN";
  employmentType: "REGULAR" | "CONTRACT" | "PART_TIME";
  joinedOn: string;
};

export const ROLE_LABELS: Record<Me["role"], string> = {
  EMPLOYEE: "一般社員",
  MANAGER: "管理職",
  CONTENT_EDITOR: "コンテンツ編集者",
  SYSTEM_ADMIN: "システム管理者",
};

export const meQueryKey = ["me"] as const;

export function fetchMe(): Promise<Me> {
  return apiFetch<Me>("/api/v1/me");
}

/** ログインする。メールアドレスかパスワードの誤りは 401 になるので、ログイン画面へ戻さずエラーとして返す */
export function login(email: string, password: string): Promise<Me> {
  return apiFetch<Me>("/api/v1/auth/login", { method: "POST", json: { email, password } }, () => {});
}

export function logout(): Promise<void> {
  return apiFetch<void>("/api/v1/auth/logout", { method: "POST" }, () => {});
}
