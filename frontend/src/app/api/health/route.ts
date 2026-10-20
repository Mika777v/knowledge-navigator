import { connection } from "next/server";
import { backendUrl } from "@/lib/bff/config";

/**
 * frontend のヘルスチェック。frontend 自身が動いていれば 200 を返し、backend の状態は参考情報として含める。
 */
export async function GET() {
  // ビルド時に事前生成させず、毎回実行する
  await connection();

  let backend = "DOWN";
  try {
    const res = await fetch(`${backendUrl()}/actuator/health/readiness`, {
      signal: AbortSignal.timeout(3_000),
    });
    if (res.ok) backend = ((await res.json()) as { status?: string }).status ?? "UNKNOWN";
  } catch {
    // backend が落ちていても frontend の生存は報告する
  }
  return Response.json({ status: "UP", backend });
}
