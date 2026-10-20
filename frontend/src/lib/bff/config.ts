/** backend（Spring Boot）の URL。実行時の環境変数から読む（ビルド済みイメージを環境ごとに使い回すため） */
export function backendUrl(): string {
  return (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}
