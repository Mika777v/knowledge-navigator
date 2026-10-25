import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";
import { login } from "./helpers";

/** data/pdf のダミー規程（ファイル名に日本語を含むため、コードの接頭辞で探す） */
function regulationPdf(prefix: string): string {
  const dir = path.resolve(__dirname, "..", "..", "data", "pdf");
  const name = readdirSync(dir).find((f) => f.startsWith(prefix));
  if (!name) throw new Error(`${prefix} の PDF がありません`);
  return path.join(dir, name);
}

test("コンテンツ担当者が規程の PDF を取り込むと、原本とページ付きの下書きの版ができる", async ({ page }) => {
  // 同じ環境で何度実行しても重ならないよう、実行ごとにちがう版ラベルにする
  const label = `E${Date.now().toString(36).toUpperCase()}`.slice(0, 20);
  await login(page, "ito@example.com");
  await page.goto("/admin/documents");
  await page.getByRole("link", { name: "慶弔見舞金規程" }).click();

  await page.getByRole("button", { name: "PDF から版を取り込む" }).click();
  // 日本語のパスは OS のロケールによって渡せないことがあるため、中身を直接渡す
  await page.locator("#import-file").setInputFiles({
    name: "慶弔見舞金規程.pdf",
    mimeType: "application/pdf",
    buffer: readFileSync(regulationPdf("REG-GA-001_")),
  });
  await page.getByLabel("版ラベル").fill(label);
  // 版の編集欄にも「施行日」があるため、取り込みの欄を指定する
  await page.locator("#import-effective").fill("2026-10-01");
  await page.getByRole("button", { name: "取り込む", exact: true }).click();

  // backend のワーカーが取り込むと、できた下書きの版が開く
  await expect(page.getByRole("heading", { level: 3, name: new RegExp(`^${label}`) })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole("link", { name: "原本（PDF）を開く" })).toBeVisible();
  const sections = page.locator("details", { hasText: "条・見出しの分かれ方" });
  await expect(sections.getByRole("listitem").filter({ hasText: "第15条（改廃）" })).toContainText("p.3");
});
