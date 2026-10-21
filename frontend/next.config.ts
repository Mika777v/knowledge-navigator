import type { NextConfig } from "next";
import { securityHeaders } from "./src/lib/security-headers";

const nextConfig: NextConfig = {
  // Docker イメージを小さくするため、実行に必要なファイルだけを .next/standalone に出力する
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  // 使っているフレームワークを応答ヘッダーで知らせない
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders({
          isDev: process.env.NODE_ENV === "development",
          // HTTPS で公開する環境では HTTPS_ONLY=true にする（ローカルの http では付けない）。
          // ヘッダーは next build の時点で確定するため、実行時ではなくビルド時に指定する
          httpsOnly: process.env.HTTPS_ONLY === "true",
        }),
      },
    ];
  },
};

export default nextConfig;
