import { ApiError } from "@/lib/api/http";

/** 読み込み中・エラーの表示（詳細画面で共通） */
export function QueryState({ isPending, error }: { isPending: boolean; error: unknown }) {
  if (isPending) {
    return <p className="text-muted-foreground">読み込み中…</p>;
  }
  if (error instanceof ApiError && error.status === 404) {
    // 権限がない場合も 404 になる（存在するかどうかを区別しない）
    return <p role="alert">見つかりませんでした。削除されたか、閲覧する権限がない可能性があります。</p>;
  }
  return <p role="alert">表示できませんでした。時間をおいて再度お試しください。</p>;
}
