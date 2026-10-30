# PDF の原本の置き場所（ADR-0010）。公開しない。terraform destroy で中身ごと消す
resource "aws_s3_bucket" "files" {
  bucket_prefix = "${var.name}-files-"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "files" {
  bucket                  = aws_s3_bucket.files.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 暗号化は S3 の標準の鍵（SSE-S3）で十分とする。自分の鍵（KMS）は月額がかかり、ダミーデータだけのデモには要らない
#trivy:ignore:AVD-AWS-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "files" {
  bucket = aws_s3_bucket.files.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
