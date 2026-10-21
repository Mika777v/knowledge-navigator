import type { Metadata } from "next";
import { AccountView } from "./account-view";

export const metadata: Metadata = { title: "アカウント | 社内規程ナビ" };

export default function AccountPage() {
  return <AccountView />;
}
