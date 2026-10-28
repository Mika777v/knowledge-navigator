#!/usr/bin/env bash
# Codespaces（Dev Containers）を作ったときに1回だけ動く。devcontainer.json の postCreateCommand から呼ぶ
# 1つが失敗しても残りは実行し、最後に失敗があったことを知らせる（#163: && でつないでいたため、
# 前のコマンドが失敗すると PyYAML が入らなかった）
set -u
status=0

# pnpm は frontend/package.json の packageManager の版を corepack で使う。
# sudo は PATH を決まった値に置き換えるため、Node の feature が入れた corepack が見つかるよう PATH を渡す
if ! sudo env "PATH=$PATH" corepack enable; then
  echo "警告: corepack enable に失敗しました。pnpm を使う前に sudo env \"PATH=\$PATH\" corepack enable を実行してください" >&2
  status=1
fi

# 評価バッチ（make eval）が使う PyYAML。make eval と同じ python3 に入れる（pip コマンドが別の Python を指すことがあるため）
if ! python3 -m pip install --user --quiet pyyaml || ! python3 -c 'import yaml'; then
  echo "警告: PyYAML を入れられませんでした。make eval の前に python3 -m pip install --user pyyaml を実行してください" >&2
  status=1
fi

exit "$status"
