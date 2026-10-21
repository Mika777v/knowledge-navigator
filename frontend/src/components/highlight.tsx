/** 本文中の検索語を <mark> で強調する。HTML として解釈せず、React のテキストとして描画する */
export function Highlight({ text, terms }: { text: string; terms: readonly string[] }) {
  const usable = terms.filter((t) => t.trim().length > 0);
  if (usable.length === 0) return <>{text}</>;
  const pattern = new RegExp(`(${usable.map(escapeRegExp).join("|")})`, "gi");
  const lowerTerms = new Set(usable.map((t) => t.toLowerCase()));
  return (
    <>
      {text.split(pattern).map((part, i) =>
        lowerTerms.has(part.toLowerCase()) ? (
          <mark key={i} className="rounded-sm bg-yellow-200 px-0.5 text-inherit dark:bg-yellow-700/60">
            {part}
          </mark>
        ) : (
          part
        ),
      )}
    </>
  );
}

function escapeRegExp(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
