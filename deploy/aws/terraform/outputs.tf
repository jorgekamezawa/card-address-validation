output "app_url" {
  description = "Public URL of the application (via the ALB)."
  value       = "http://${aws_lb.this.dns_name}"
}

output "ecr_app_repository_url" {
  description = "ECR repository for the application image."
  value       = aws_ecr_repository.app.repository_url
}

output "ecr_provider_mock_repository_url" {
  description = "ECR repository for the WireMock provider-mock image."
  value       = aws_ecr_repository.provider_mock.repository_url
}

output "rds_endpoint" {
  description = "RDS Postgres endpoint."
  value       = aws_db_instance.postgres.endpoint
}

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint."
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}
