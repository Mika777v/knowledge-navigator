import type { Element, ElementContent, Root } from "hast";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

/**
 * 条文・FAQ の回答（Markdown）を表示する。表（GFM）にも対応する。
 *
 * <ul>
 *   <li>本文中の HTML は描画しない（skipHtml）。リンクの URL は react-markdown の既定の確認で javascript: などを除く
 *   <li>terms を渡すと、本文中の語を <mark> で強調する（HTML の文字列は組み立てず、構文木の上で囲む）
 * </ul>
 */
export function Markdown({ text, terms = [] }: { text: string; terms?: readonly string[] }) {
  return (
    <div className="markdown">
      <ReactMarkdown skipHtml remarkPlugins={[remarkGfm]} rehypePlugins={[[rehypeHighlight, terms]]}>
        {text}
      </ReactMarkdown>
    </div>
  );
}

/** 構文木のテキストを検索語で分け、一致した部分を <mark> にする rehype プラグイン */
export function rehypeHighlight(terms: readonly string[]) {
  const usable = terms.filter((t) => t.trim().length > 0);
  return (tree: Root) => {
    if (usable.length === 0) return;
    const pattern = new RegExp(`(${usable.map(escapeRegExp).join("|")})`, "gi");
    const lower = new Set(usable.map((t) => t.toLowerCase()));
    const visit = (node: Root | Element) => {
      node.children = node.children.flatMap((child): ElementContent[] => {
        if (child.type === "element") {
          if (child.tagName !== "code" && child.tagName !== "pre") visit(child);
          return [child];
        }
        if (child.type !== "text") return [child as ElementContent];
        return child.value
          .split(pattern)
          .filter((part) => part !== "")
          .map((part) =>
            lower.has(part.toLowerCase())
              ? { type: "element", tagName: "mark", properties: {}, children: [{ type: "text", value: part }] }
              : { type: "text", value: part },
          );
      }) as Root["children"] & Element["children"];
    };
    visit(tree);
  };
}

function escapeRegExp(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
