# 秘密の値は SSM Parameter Store（SecureString）に置き、EC2 の起動のたびに .env へ書き出す（ADR-0015 決定 6）。
# 値はこの Terraform が作る。リポジトリには書かない
resource "random_password" "user_hash_secret" {
  length  = 48
  special = false
}

resource "random_password" "demo_user" {
  length  = 16
  special = false
}

locals {
  parameter_prefix = "/${var.name}"
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.parameter_prefix}/POSTGRES_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "user_hash_secret" {
  name  = "${local.parameter_prefix}/KNAVI_USER_HASH_SECRET"
  type  = "SecureString"
  value = random_password.user_hash_secret.result
}

# デモユーザーのパスワード。見せる相手に伝える。terraform output -raw demo_user_password で確かめる
resource "aws_ssm_parameter" "demo_user_password" {
  name  = "${local.parameter_prefix}/DEMO_USER_PASSWORD"
  type  = "SecureString"
  value = random_password.demo_user.result
}
