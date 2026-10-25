import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithQuery } from "@/test/render";
import { EditDocument } from "./[id]/edit-document";
import { withoutFrontMatter } from "./[id]/versions-panel";
import type { AdminDocument, VersionDetail, VersionSummary } from "./document-api";

vi.mock("next/navigation", () => ({ useParams: () => ({ id: "d1" }), useRouter: () => ({ push: vi.fn() }) }));

const doc = (over: Partial<AdminDocument> = {}): AdminDocument => ({
  id: "d1",
  code: "REG-GA-001",
  title: "慶弔見舞金規程",
  summary: "慶弔時の祝金・見舞金",
  documentType: "REGULATION",
  category: { slug: "welfare", name: "福利厚生" },
  ownerDepartment: { code: "GA", name: "総務部" },
  visibility: "INTERNAL",
  status: "PUBLISHED",
  currentVersionId: "v1",
  reviewDueOn: null as never,
  tags: ["お金"],
  rowVersion: 4,
  ...over,
});

const summary = (over: Partial<VersionSummary>): VersionSummary => ({
  id: "v1",
  versionLabel: "v2.1",
  effectiveFrom: "2026-04-01",
  changeNote: null as never,
  publishedAt: "2026-03-01T00:00:00Z",
  current: true,
  sectionCount: 12,
  sourceFileId: null as never,
  ...over,
});

const version = (over: Partial<VersionDetail> = {}): VersionDetail => ({
  id: "v1",
  versionLabel: "v2.1",
  effectiveFrom: "2026-04-01",
  changeNote: null as never,
  publishedAt: "2026-03-01T00:00:00Z",
  bodyMarkdown: "### 第5条（結婚祝金）\n\n30,000円を支給する。",
  sections: [
    {
      id: "s5",
      ordinal: 5,
      path: null as never,
      articleNo: "第5条",
      heading: "結婚祝金",
      content: "30,000円",
      pageFrom: null as never,
      pageTo: null as never,
    },
  ],
  sourceFileId: null as never,
  ...over,
});

type Call = { url: string; method: string; ifMatch: string | null; body: unknown };

function stubApi(handle: (call: Call) => Response | undefined) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(async (input, init) => {
      const call = {
        url: String(input),
        method: init?.method ?? "GET",
        ifMatch: new Headers(init?.headers).get("If-Match"),
        // アップロード（FormData）はそのまま、JSON は読み取って記録する
        body: typeof init?.body === "string" ? JSON.parse(init.body) : init?.body,
      };
      calls.push(call);
      const res = handle(call);
      if (res) return res;
      if (call.url === "/api/v1/categories") return Response.json([{ slug: "welfare", name: "福利厚生", children: [] }]);
      if (call.url === "/api/v1/tags")
        return Response.json([
          { name: "正社員", type: "TARGET", documentCount: 2 },
          { name: "お金", type: "TOPIC", documentCount: 3 },
          { name: "家族", type: "TOPIC", documentCount: 1 },
        ]);
      if (call.url === "/api/v1/admin/documents/d1" && call.method === "GET") return Response.json(doc());
      throw new Error(`unexpected ${call.method} ${call.url}`);
    }),
  );
  return calls;
}

describe("withoutFrontMatter", () => {
  it("先頭の front matter だけを除く", () => {
    expect(withoutFrontMatter("---\ncode: REG-1\ntitle: 規程\n---\n\n### 第1条（目的）\n\n本文\n\n---\n")).toBe(
      "### 第1条（目的）\n\n本文\n\n---\n",
    );
    expect(withoutFrontMatter("### 第1条\n本文")).toBe("### 第1条\n本文");
  });
});

describe("EditDocument", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("文書の情報を、版番号を付けて保存する", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") return Response.json([summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url === "/api/v1/admin/documents/d1" && method === "PUT") return Response.json(doc({ rowVersion: 5, title: "慶弔見舞金規程（改）" }));
    });
    renderWithQuery(<EditDocument />);

    fireEvent.change(await screen.findByLabelText("文書名"), { target: { value: "慶弔見舞金規程（改）" } });
    fireEvent.click(screen.getByRole("button", { name: "文書の情報を保存" }));

    expect(await screen.findByText("文書の情報を保存しました。")).toBeInTheDocument();
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.ifMatch).toBe('"4"');
    expect(put?.body).toMatchObject({ title: "慶弔見舞金規程（改）", documentType: "REGULATION", categorySlug: "welfare" });
  });

  it("タグは、使われているタグを押すか新しい名前を入れて付け外しし、属性と一緒に保存する", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") return Response.json([summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url === "/api/v1/admin/documents/d1" && method === "PUT") return Response.json(doc({ rowVersion: 5 }));
    });
    renderWithQuery(<EditDocument />);

    expect(await screen.findByRole("button", { name: "お金" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "お金" }));
    fireEvent.click(screen.getByRole("button", { name: "家族" }));
    fireEvent.change(screen.getByLabelText("新しいタグ"), { target: { value: "  慶弔 " } });
    fireEvent.click(screen.getByRole("button", { name: "タグを追加" }));
    expect(screen.getByRole("button", { name: "慶弔" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "文書の情報を保存" }));

    expect(await screen.findByText("文書の情報を保存しました。")).toBeInTheDocument();
    expect(calls.find((c) => c.method === "PUT")?.body).toMatchObject({ tags: ["家族", "慶弔"] });
  });

  it("新しい版は、最新の版の本文を初期値にして下書きで作る", async () => {
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") return Response.json([summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url.endsWith("/versions") && method === "POST") {
        return Response.json(version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never }), { status: 201 });
      }
      if (url.endsWith("/versions/v2")) return Response.json(version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never }));
    });
    renderWithQuery(<EditDocument />);

    fireEvent.click(await screen.findByRole("button", { name: "新しい版を作る" }));
    const body = await screen.findByLabelText("本文（Markdown）");
    expect(body).toHaveValue("### 第5条（結婚祝金）\n\n30,000円を支給する。");
    fireEvent.change(screen.getByLabelText("版"), { target: { value: "v2.2" } });
    fireEvent.change(screen.getByLabelText("施行日"), { target: { value: "2026-10-01" } });
    fireEvent.click(screen.getByRole("button", { name: "下書きとして作成" }));

    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({
      versionLabel: "v2.2",
      effectiveFrom: "2026-10-01",
      bodyMarkdown: "### 第5条（結婚祝金）\n\n30,000円を支給する。",
    });
    expect(await screen.findByText("下書き（編集できます）")).toBeInTheDocument();
  });

  it("下書きは保存して条の分かれ方を確かめ、保存してから文書の版番号で公開する", async () => {
    const draft = version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never, sections: [] });
    const saved = version({ id: "v2", versionLabel: "v2.2", publishedAt: null as never });
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") {
        return Response.json([summary({ id: "v2", versionLabel: "v2.2", publishedAt: null as never, current: false, sectionCount: 0 }), summary({})]);
      }
      if (url.endsWith("/versions/v2") && method === "GET") return Response.json(draft);
      if (url.endsWith("/versions/v2") && method === "PUT") return Response.json(saved);
      if (url.endsWith("/publish")) return Response.json(doc({ rowVersion: 5, currentVersionId: "v2" }));
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    renderWithQuery(<EditDocument />);

    expect(await screen.findByText("下書き（編集できます）")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "下書きを保存" }));
    expect(await screen.findByText("保存しました。本文は 1 個の条・見出しに分かれました。")).toBeInTheDocument();
    const split = screen.getByText(/条・見出しの分かれ方/).closest("details")!;
    expect(within(split).getByText("第5条（結婚祝金）")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "保存してこの版を公開する" }));
    expect(await screen.findByText("v2.2 を公開しました。")).toBeInTheDocument();
    const publish = calls.find((c) => c.url.endsWith("/publish"));
    expect(publish?.ifMatch).toBe('"4"');
    expect(publish?.body).toEqual({ versionId: "v2" });
    // 公開の前に本文を保存している
    expect(calls.filter((c) => c.method === "PUT").length).toBe(2);
  });
});

describe("PDF からの取り込み", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });
  afterEach(() => vi.unstubAllGlobals());

  const job = (status: string, over: object = {}) => ({
    id: "j1",
    documentId: "d1",
    fileId: "f1",
    fileName: "慶弔見舞金規程.pdf",
    versionLabel: "v3.0",
    effectiveFrom: "2026-10-01",
    changeNote: null,
    status,
    attempts: 1,
    errorMessage: null,
    resultVersionId: null,
    createdAt: "2026-10-25T00:00:00Z",
    startedAt: null,
    finishedAt: null,
    ...over,
  });

  async function startImport() {
    fireEvent.click(await screen.findByRole("button", { name: "PDF から版を取り込む" }));
    const pdf = new File(["%PDF-1.4"], "慶弔見舞金規程.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText("PDF ファイル（20MB まで）"), { target: { files: [pdf] } });
    fireEvent.change(screen.getByLabelText("版ラベル"), { target: { value: " v3.0 " } });
    fireEvent.change(screen.getByLabelText("施行日"), { target: { value: "2026-10-01" } });
    fireEvent.click(screen.getByRole("button", { name: "取り込む" }));
  }

  it("アップロードして取り込みを頼み、終わったらできた版を開く", async () => {
    let polls = 0;
    const calls = stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET")
        return Response.json(polls >= 2 ? [summary({ id: "v3", versionLabel: "v3.0", publishedAt: null as never, current: false, sourceFileId: "f1" }), summary({})] : [summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url.endsWith("/versions/v3"))
        return Response.json(
          version({
            id: "v3",
            versionLabel: "v3.0",
            publishedAt: null as never,
            sourceFileId: "f1",
            sections: [{ ...version().sections[0], pageFrom: 2, pageTo: 3 }],
          }),
        );
      if (url === "/api/v1/admin/files" && method === "POST") return Response.json({ id: "f1" }, { status: 201 });
      if (url === "/api/v1/admin/documents/d1/ingestion-jobs" && method === "POST") return Response.json(job("QUEUED"), { status: 202 });
      if (url === "/api/v1/admin/ingestion-jobs/j1") {
        polls++;
        return Response.json(polls < 2 ? job("RUNNING") : job("SUCCEEDED", { resultVersionId: "v3" }));
      }
    });
    renderWithQuery(<EditDocument />);

    await startImport();

    expect(await screen.findByText(/PDF を読み取っています/)).toBeInTheDocument();
    expect(calls.find((c) => c.url === "/api/v1/admin/documents/d1/ingestion-jobs")?.body).toEqual({
      fileId: "f1",
      versionLabel: "v3.0",
      effectiveFrom: "2026-10-01",
    });
    // 終わったら、できた下書きの版を開き、原本と条ごとのページを確かめられる
    expect(await screen.findByText("v3.0", { selector: "h3" }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "原本（PDF）を開く" })).toHaveAttribute("href", "/api/v1/admin/files/f1/content");
    expect(screen.getByRole("link", { name: "p.2–3" })).toHaveAttribute("href", "/api/v1/admin/files/f1/content#page=2");
  });

  it("取り込めなかったら、理由を出して取り込み直せる", async () => {
    stubApi(({ url, method }) => {
      if (url.endsWith("/versions") && method === "GET") return Response.json([summary({})]);
      if (url.endsWith("/versions/v1")) return Response.json(version());
      if (url === "/api/v1/admin/files" && method === "POST") return Response.json({ id: "f1" }, { status: 201 });
      if (url === "/api/v1/admin/documents/d1/ingestion-jobs" && method === "POST") return Response.json(job("QUEUED"), { status: 202 });
      if (url === "/api/v1/admin/ingestion-jobs/j1")
        return Response.json(job("FAILED", { errorMessage: "PDF から文字を取り出せませんでした。画像だけの PDF（スキャンしたもの）は取り込めません。" }));
    });
    renderWithQuery(<EditDocument />);

    await startImport();

    expect(await screen.findByText(/取り込めませんでした。/)).toBeInTheDocument();
    expect(screen.getByText(/スキャンしたもの/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "別の PDF・版ラベルで取り込み直す" }));
    expect(screen.getByLabelText("PDF ファイル（20MB まで）")).toBeInTheDocument();
  });
});
