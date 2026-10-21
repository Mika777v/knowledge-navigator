import Link from "next/link";
import type { BasisView } from "@/lib/api/types";
import { articleTitle, sectionAnchor } from "@/lib/content";

/** 根拠の条文へのリンク（条が分かれば文書内のその条へ移動する） */
export function BasisLink({ basis }: { basis: BasisView }) {
  const href = basis.sectionId
    ? `/documents/${basis.documentId}#${sectionAnchor(basis.sectionId)}`
    : `/documents/${basis.documentId}`;
  const article = articleTitle(basis.articleNo, basis.heading);
  return (
    <Link className="underline" href={href}>
      {basis.documentTitle}
      {article && ` ${article}`}
    </Link>
  );
}
