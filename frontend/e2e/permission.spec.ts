import { expect, type Page, test } from "@playwright/test";
import { login } from "./helpers";

// ダミーデータの管理職限定（公開範囲 MANAGERS）の FAQ と制度
const QUESTION = "部下からハラスメントの相談を受けた";
const MANAGER_FAQ = "部下からハラスメントの相談を受けたらどうすればいいですか？";
const MANAGER_PROGRAM = "部下の妊娠・出産・介護の申出への対応（管理職向け）";

async function search(page: Page, q: string) {
  await page.goto("/search?q=" + encodeURIComponent(q));
  await expect(page.getByRole("region", { name: "問い合わせ先" })).toBeVisible();
}

test("管理職限定の FAQ・制度は、管理職には出て、一般社員には検索にも詳細にも出ない", async ({ page, browser }) => {
  await login(page, "tanaka@example.com");
  await search(page, QUESTION);
  const faqLink = page.getByRole("region", { name: "よくある質問" }).getByRole("link", { name: MANAGER_FAQ });
  await expect(faqLink).toBeVisible();
  await expect(page.getByRole("region", { name: "使える制度" }).getByRole("link", { name: MANAGER_PROGRAM })).toBeVisible();
  const faqUrl = await faqLink.getAttribute("href");
  expect(faqUrl).toMatch(/^\/faqs\//);

  const employee = await browser.newPage();
  await login(employee, "sato@example.com");
  await search(employee, QUESTION);
  // 同じ質問でも、一般社員の結果には出ない（ほかの FAQ は出る）
  await expect(employee.getByRole("region", { name: "よくある質問" }).getByRole("link").first()).toBeVisible();
  await expect(employee.getByRole("link", { name: MANAGER_FAQ })).toHaveCount(0);
  await expect(employee.getByRole("link", { name: MANAGER_PROGRAM })).toHaveCount(0);

  // URL を直接開いても、存在するかどうかが分からない形で断る
  await employee.goto(faqUrl ?? "/");
  await expect(employee.getByRole("main").getByRole("alert")).toHaveText(/見つかりませんでした。削除されたか、閲覧する権限がない可能性があります。/);
  await expect(employee.getByRole("heading", { name: MANAGER_FAQ })).toHaveCount(0);
});
