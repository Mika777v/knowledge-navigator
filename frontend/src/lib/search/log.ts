import { apiFetch } from "@/lib/api/http";

export type ClickTarget = "PROGRAM" | "DOCUMENT" | "SECTION" | "FAQ" | "CONTACT";

/**
 * 検索結果のクリックを記録する（検索の改善に使う）。画面の移動を妨げないよう待たずに送り、失敗しても利用者には知らせない。
 * keepalive を付けて、ページを移動しても送信が途中で止まらないようにする。
 */
export function recordClick(searchLogId: string | null | undefined, targetType: ClickTarget, targetId: string, rank: number) {
  if (!searchLogId) return;
  void apiFetch<void>(
    `/api/v1/search-logs/${encodeURIComponent(searchLogId)}/clicks`,
    { method: "POST", json: { targetType, targetId, rank }, keepalive: true },
    () => {},
  ).catch(() => {});
}

export function sendFeedback(searchLogId: string, helpful: boolean, comment?: string): Promise<void> {
  return apiFetch<void>(`/api/v1/search-logs/${encodeURIComponent(searchLogId)}/feedback`, {
    method: "POST",
    json: { helpful, comment: comment?.trim() || null },
  });
}
