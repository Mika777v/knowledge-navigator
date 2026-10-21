/**
 * ダミーデータの投入（開発・デモ用）。data/seed の YAML と規程の Markdown を DB に取り込む。
 *
 * <p>文書は content モジュールの公開 API（DocumentImport）経由で取り込み、条単位の分割を本番と同じ処理で行う。
 * それ以外のマスタは投入を単純にするため SQL で直接書き込む（開発用ツールに限った割り切り）。
 */
@org.springframework.modulith.ApplicationModule(displayName = "Seed")
package com.example.knavi.seed;
