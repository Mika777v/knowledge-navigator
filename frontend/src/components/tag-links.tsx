import Link from "next/link";

/** 文書のタグ。押すと、そのタグの規程の一覧へ移動する */
export function TagLinks({ tags, label = "タグ" }: { tags: string[]; label?: string }) {
  if (tags.length === 0) return null;
  return (
    <ul aria-label={label} className="flex flex-wrap gap-1.5">
      {tags.map((tag) => (
        <li key={tag}>
          <Link
            href={`/documents?tag=${encodeURIComponent(tag)}`}
            className="rounded-full border border-border px-2.5 py-0.5 text-xs hover:bg-foreground/5"
          >
            {tag}
          </Link>
        </li>
      ))}
    </ul>
  );
}
