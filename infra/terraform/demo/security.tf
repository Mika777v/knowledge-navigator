# セキュリティグループ（通信の許可リスト）。
# 画面（EC2）: 許可した IP からの 443 だけを受け付ける。SSH・80 は開けない（操作は Session Manager）
# DB（RDS）: EC2 からの 5432 だけを受け付ける
resource "aws_security_group" "web" {
  name        = "${var.name}-web"
  description = "knavi demo web (EC2)"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-web" }
}

resource "aws_vpc_security_group_ingress_rule" "web_https" {
  for_each = toset(var.allowed_cidrs)

  security_group_id = aws_security_group.web.id
  description       = "HTTPS from allowed network"
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# 外へ出ていく通信（ghcr.io からのイメージの取得・OS の更新・Session Manager・Let's Encrypt・Route 53）。
# 宛先の IP は決まらないため、ポートだけを 443・80 に絞る
#trivy:ignore:AVD-AWS-0104
resource "aws_vpc_security_group_egress_rule" "web_https_out" {
  security_group_id = aws_security_group.web.id
  description       = "HTTPS to internet (images, updates, SSM, ACME)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

#trivy:ignore:AVD-AWS-0104
resource "aws_vpc_security_group_egress_rule" "web_http_out" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP to internet (OS package mirrors)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_egress_rule" "web_to_db" {
  security_group_id            = aws_security_group.web.id
  description                  = "PostgreSQL to RDS"
  referenced_security_group_id = aws_security_group.db.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "knavi demo database (RDS)"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-db" }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_web" {
  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL from web"
  referenced_security_group_id = aws_security_group.web.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
