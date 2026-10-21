import type { Metadata } from "next";
import { AnalyticsDashboard } from "./analytics-dashboard";

export const metadata: Metadata = { title: "検索の分析 | 社内規程ナビ" };

export default function AnalyticsPage() {
  return <AnalyticsDashboard />;
}
