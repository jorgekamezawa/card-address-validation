# ---------------------------------------------------------------------------
# Security groups
# ---------------------------------------------------------------------------

resource "aws_security_group" "ecs" {
  name        = "${local.name}-ecs"
  description = "ECS tasks"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "App port from the ALB"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "All outbound (ECR pull, RDS, Redis)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds"
  description = "Postgres, reachable only from ECS tasks"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Postgres from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
}

resource "aws_security_group" "redis" {
  name        = "${local.name}-redis"
  description = "Redis, reachable only from ECS tasks"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Redis from ECS"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
}

# ---------------------------------------------------------------------------
# RDS Postgres (audit log) — demo profile: single-AZ, easy to destroy
# ---------------------------------------------------------------------------

resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "postgres" {
  identifier     = "${local.name}-postgres"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  multi_az               = var.db_multi_az
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # Demo: no protection so `terraform destroy` works cleanly.
  deletion_protection = false
  skip_final_snapshot = true
}

# ---------------------------------------------------------------------------
# ElastiCache Redis (cache) — demo profile: single node, plaintext client
# ---------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "this" {
  name       = "${local.name}-redis"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${local.name}-redis"
  description          = "CEP lookup cache"
  engine               = "redis"
  node_type            = var.redis_node_type
  num_cache_clusters   = 1

  # Single node: no failover, and no transit encryption (the app connects in plaintext).
  automatic_failover_enabled = false
  transit_encryption_enabled = false
  at_rest_encryption_enabled = true

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.redis.id]
}
