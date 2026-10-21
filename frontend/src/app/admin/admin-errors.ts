import { ApiError } from "@/lib/api/http";

/** 管理画面の保存・公開のエラーを、利用者向けの文にする */
export function adminErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 412) {
    return "ほかの人が先に更新しました。画面を読み込み直してから、もう一度編集してください。";
  }
  return error instanceof ApiError ? error.detail : "処理できませんでした。時間をおいて再度お試しください。";
}
