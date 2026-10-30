# デモ環境（ADR-0015）の AWS の資源。オーナーの PC から terraform apply で作り、terraform destroy で消す。
# 状態ファイル（terraform.tfstate）はオーナーの PC に置く。DB のパスワードなどの秘密が入るため、リポジトリには入れない（.gitignore）
terraform {
  required_version = ">= 1.16.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.69.1"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.9.1"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.name
      ManagedBy = "terraform"
    }
  }
}
