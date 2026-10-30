output "url" {
  description = "画面の URL"
  value       = var.domain_name == "" ? "https://${aws_eip.web.public_ip}" : "https://${var.domain_name}"
}

output "instance_id" {
  description = "EC2 の ID（Session Manager で入るときに使う）"
  value       = aws_instance.web.id
}

output "public_ip" {
  description = "EC2 の IP（固定）"
  value       = aws_eip.web.public_ip
}

output "db_address" {
  description = "RDS の接続先（VPC の中からだけ届く）"
  value       = aws_db_instance.main.address
}

output "bucket" {
  description = "PDF の原本を置く S3 のバケット"
  value       = aws_s3_bucket.files.id
}

output "demo_user_password" {
  description = "デモユーザーのパスワード（terraform output -raw demo_user_password で表示）"
  value       = random_password.demo_user.result
  sensitive   = true
}
