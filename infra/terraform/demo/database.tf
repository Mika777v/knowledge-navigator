# RDS for PostgreSQL 17。pg_bigm・pgvector・pgcrypto は、backend の Flyway が最初の起動で CREATE EXTENSION する
# （マスターユーザーで接続するため作れる）。プライベートサブネットに置き、外からは届かない
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "knavi"
  username = "knavi"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  # デモは使わない期間に terraform destroy で消すため、消すときの確認・最後のスナップショットを作らない
  deletion_protection = false
  skip_final_snapshot = true
}
