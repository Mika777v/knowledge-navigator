import { expect, type Page } from "@playwright/test";

/** デモユーザーのパスワード（make seed で設定したもの） */
export const PASSWORD = process.env.DEMO_USER_PASSWORD ?? "demo-pass-2026";

/** ログイン画面からログインし、ログイン後の画面（ヘッダーのログアウト）が表示されるまで待つ */
export async function login(page: Page, email: string, password = PASSWORD) {
  await page.goto("/login");
  await page.getByLabel("メールアドレス").fill(email);
  await page.getByLabel("パスワード").fill(password);
  await page.getByRole("button", { name: "ログイン" }).click();
  // ログイン画面にも「社内規程ナビ」の見出しがあるため、ログイン後にだけ出るものを待つ
  await expect(page.getByRole("button", { name: "ログアウト" })).toBeVisible();
}

/** ブラウザで起きたエラー（スクリプトのエラー・CSP 違反など）を集める */
export function collectErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  return errors;
}

/**
 * 実行ごとにちがう、どの検索語にも当たらない言葉を作る。
 * 時刻を英字（母音・c・p を除く）だけで表す。数字を含めると形態素解析で「26」のように区切られ、
 * ダミーデータの本文に当たってしまうため（キーワード「PC」にも当たらないよう c・p も使わない）。
 */
export function uniqueLetters(): string {
  const letters = "bdfghjklmnqrstvwxz";
  let n = Date.now();
  let word = "";
  while (n > 0) {
    word = letters[n % letters.length] + word;
    n = Math.floor(n / letters.length);
  }
  return word;
}
