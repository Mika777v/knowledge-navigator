#!/usr/bin/env bash
# デモ環境（docs/DEMO_AWS.md）の .env.demo を作る。EC2 の上で、up.sh・reset.sh から呼ぶ。
# Terraform が置いた /opt/knavi/demo.env（秘密でない値）と、SSM Parameter Store の秘密の値を合わせる
set -euo pipefail

version=${1:?版（例: v0.7.0）を指定してください}
# shellcheck source=/dev/null
source /opt/knavi/demo.env

if [ -n "${KNAVI_DOMAIN:-}" ]; then
  site=$KNAVI_DOMAIN
  tls_mode=acme
else
  # ドメインを使わないときは EC2 の固定 IP で開く（証明書は Caddy が自分で作る）
  token=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
  site=$(curl -fsS -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/public-ipv4)
  tls_mode=internal
fi

umask 077
{
  echo "KNAVI_VERSION=${version#v}"
  echo "AWS_REGION=$AWS_REGION"
  echo "DB_HOST=$DB_HOST"
  echo "KNAVI_STORAGE_BUCKET=$KNAVI_STORAGE_BUCKET"
  echo "KNAVI_SITE=$site"
  echo "KNAVI_TLS_MODE=$tls_mode"
  # 秘密の値（POSTGRES_PASSWORD・KNAVI_USER_HASH_SECRET・DEMO_USER_PASSWORD）
  aws ssm get-parameters-by-path --region "$AWS_REGION" --path "$KNAVI_PARAMETER_PREFIX/" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text |
    while IFS=$'\t' read -r name value; do echo "${name##*/}=$value"; done
} > .env.demo
