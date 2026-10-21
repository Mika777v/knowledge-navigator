"use client";

import { useEffect } from "react";

/** データの読み込み後に描画される要素へ、URL の #id でスクロールする（初回表示時はまだ要素がないため） */
export function useScrollToHash(ready: boolean) {
  useEffect(() => {
    if (!ready || !window.location.hash) return;
    const target = document.getElementById(decodeURIComponent(window.location.hash.slice(1)));
    target?.scrollIntoView();
  }, [ready]);
}
