# 画面と API を動かす EC2 1台（ADR-0015）。Ubuntu 24.04 に Docker を入れ、compose.demo.yaml で frontend・backend・Caddy を動かす。
# SSH の鍵は作らない。操作は Systems Manager の Session Manager で行う
data "aws_ssm_parameter" "ubuntu" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "web" {
  name               = "${var.name}-web"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# Session Manager で入れるようにする
resource "aws_iam_role_policy_attachment" "web_ssm" {
  role       = aws_iam_role.web.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "web" {
  # 原本のバケットだけを読み書きする（アクセスキーを作らない）
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.files.arn]
  }
  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.files.arn}/*"]
  }
  # 自分のデモ用の秘密の値だけを読む
  statement {
    actions   = ["ssm:GetParametersByPath", "ssm:GetParameter", "ssm:GetParameters"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.parameter_prefix}/*"]
  }
  # Caddy が証明書を取るときに、DNS に確認用の記録を書く（DNS-01）。ドメインを使うときだけ
  dynamic "statement" {
    for_each = var.domain_name == "" ? [] : [1]
    content {
      actions   = ["route53:ChangeResourceRecordSets", "route53:ListResourceRecordSets"]
      resources = ["arn:aws:route53:::hostedzone/${var.route53_zone_id}"]
    }
  }
  dynamic "statement" {
    for_each = var.domain_name == "" ? [] : [1]
    content {
      actions   = ["route53:GetChange", "route53:ListHostedZonesByName", "route53:ListHostedZones"]
      resources = ["*"]
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_iam_role_policy" "web" {
  name   = "${var.name}-web"
  role   = aws_iam_role.web.id
  policy = data.aws_iam_policy_document.web.json
}

resource "aws_iam_instance_profile" "web" {
  name = "${var.name}-web"
  role = aws_iam_role.web.name
}

resource "aws_instance" "web" {
  ami                         = data.aws_ssm_parameter.ubuntu.value
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.web.id]
  iam_instance_profile        = aws_iam_instance_profile.web.name
  associate_public_ip_address = true

  # IMDSv2 だけを使う（インスタンスの情報を取る口を、なりすましに強い方式に限る）
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
    # Docker のコンテナから IAM ロールの資格情報を取るため、1 段増やす
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  # 起動したときに一度だけ、Docker・AWS CLI を入れ、アプリの設定を置く場所を作る。
  # アプリの起動の手順は docs/DEMO_AWS.md
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    region           = var.region
    parameter_prefix = local.parameter_prefix
    db_host          = aws_db_instance.main.address
    bucket           = aws_s3_bucket.files.id
    domain_name      = var.domain_name
  })

  # AMI が新しくなっても、作り直さない（作り直すときは terraform apply -replace）
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = { Name = "${var.name}-web" }
}

# 止めて起動し直しても IP が変わらないようにする
resource "aws_eip" "web" {
  instance = aws_instance.web.id
  domain   = "vpc"

  tags = { Name = "${var.name}-web" }
}

resource "aws_route53_record" "web" {
  count   = var.domain_name == "" ? 0 : 1
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.web.public_ip]
}
