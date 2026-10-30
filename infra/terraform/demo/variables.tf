variable "name" {
  description = "資源の名前の頭に付ける文字列"
  type        = string
  default     = "knavi-demo"
}

variable "region" {
  description = "AWS のリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "allowed_cidrs" {
  description = "画面（443）に接続してよい IP の範囲。会社の出口の固定 IP など（例: [\"203.0.113.10/32\"]）。インターネット全体には開けない（ADR-0015）"
  type        = list(string)

  validation {
    condition     = length(var.allowed_cidrs) > 0
    error_message = "allowed_cidrs に、接続してよい IP を1つ以上書いてください。"
  }

  validation {
    condition     = alltrue([for c in var.allowed_cidrs : can(cidrnetmask(c)) && !contains(["0.0.0.0/0"], c)])
    error_message = "allowed_cidrs には IPv4 の範囲（例: 203.0.113.10/32）を書き、0.0.0.0/0（インターネット全体）は書かないでください。"
  }
}

variable "domain_name" {
  description = "画面の URL のドメイン（例: demo.example.jp）。空にすると、ドメインを使わず IP で開き、証明書は Caddy が自分で作る（ブラウザに警告が出る）"
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "domain_name を管理している Route 53 のホストゾーンの ID。domain_name が空なら使わない"
  type        = string
  default     = ""

  validation {
    condition     = var.domain_name == "" || var.route53_zone_id != ""
    error_message = "domain_name を使うときは route53_zone_id も書いてください。"
  }
}

variable "instance_type" {
  description = "EC2 の大きさ。frontend と backend（JVM）で 2GB ほど使うため、メモリ 4GB 以上"
  type        = string
  default     = "t3.medium"
}

variable "db_instance_class" {
  description = "RDS の大きさ"
  type        = string
  default     = "db.t4g.micro"
}

variable "budget_limit_usd" {
  description = "1か月の予算（米ドル）。超えそうになったら budget_email にメールが届く"
  type        = number
  default     = 60
}

variable "budget_email" {
  description = "予算の通知を受け取るメールアドレス"
  type        = string
}
