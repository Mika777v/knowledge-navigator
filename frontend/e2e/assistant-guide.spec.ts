import { expect, test } from "@playwright/test";
import { collectErrors, login } from "./helpers";

// 案内（ADR-0014）は LLM を使わないため、AI を動かさない CI でも同じ結果になる
test("検索結果から結婚の案内へ移り、答えで制度を判定してチェックリストを出す", async ({ page }) => {
  const errors = collectErrors(page);
  // 佐藤さんは正社員（ダミーデータ）
  await login(page, "sato@example.com");
  await page.goto("/search?q=" + encodeURIComponent("結婚"));

  await page.getByRole("link", { name: "結婚の手続きを、質問に答えて整理する" }).click();
  await expect(page).toHaveURL(/\/assistant\/guide\?event=MARRIAGE/);
  await expect(page.getByRole("heading", { level: 1, name: "結婚の手続き" })).toBeVisible();

  // CI では AI をオフにしているので、状況を書いても読み取らず、その旨を出す（ADR-0014 の決定 2）
  await page.getByLabel(/状況を書くと/).fill("パートで働いています");
  await page.getByRole("button", { name: "AI に読み取ってもらう" }).click();
  await expect(page.getByText(/AI による読み取りは、いまは使えない設定/)).toBeVisible();

  // 雇用形態はプロフィールから選ばれている。勤続は答えるまで要確認
  await expect(page.getByRole("radio", { name: "正社員", exact: true })).toBeChecked();
  const gift = page.getByRole("listitem", { name: "結婚祝金" });
  await expect(gift.getByText("要確認")).toBeVisible();
  await expect(gift.getByRole("link", { name: /慶弔見舞金規程/ }).first()).toBeVisible();

  await page.getByRole("radio", { name: "はい" }).check();
  await expect(gift.getByText("対象", { exact: true })).toBeVisible();
  const checklist = page.getByRole("region", { name: "手続きのチェックリスト" });
  await expect(checklist.getByText("戸籍謄本または婚姻届受理証明書の写し")).toBeVisible();
  await expect(checklist.getByText("総務部 庶務グループ").first()).toBeVisible();

  // パートタイム社員を選ぶと、住宅手当は理由つきで対象外になり、チェックリストから外れる
  await page.getByRole("radio", { name: "パートタイム社員" }).check();
  const housing = page.getByRole("listitem", { name: "住宅手当" });
  await expect(housing.getByText("対象外")).toBeVisible();
  await expect(housing.getByText(/当てはまらない条件: 雇用形態を教えてください。/)).toBeVisible();
  await expect(checklist.getByText("住宅手当", { exact: true })).toHaveCount(0);

  expect(errors).toEqual([]);
});
