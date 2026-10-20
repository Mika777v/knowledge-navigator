import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/** Tailwind のクラスを結合し、競合するものは後勝ちにする（shadcn/ui の規約） */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
