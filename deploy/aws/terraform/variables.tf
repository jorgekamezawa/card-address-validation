variable "aws_region" {
  description = "AWS region to deploy into (us-east-1 is the cheapest and has all services)."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (e.g. prod, demo)."
  type        = string
  default     = "demo"
}

variable "app_name" {
  description = "Application name, used to name resources."
  type        = string
  default     = "card-address-validation"
}

# --- Application container ---

variable "image_tag" {
  description = "Tag pushed to both ECR repos (app + provider mock)."
  type        = string
  default     = "latest"
}

variable "container_port" {
  description = "Port the Spring Boot app listens on."
  type        = number
  default     = 8080
}

variable "provider_mock_port" {
  description = "Port the WireMock sidecar listens on (reached at localhost by the app)."
  type        = number
  default     = 8081
}

variable "desired_count" {
  description = "Number of ECS tasks to run."
  type        = number
  default     = 1
}

variable "task_cpu" {
  description = "Fargate task CPU units (shared by app + mock)."
  type        = number
  default     = 1024
}

variable "task_memory" {
  description = "Fargate task memory (MiB, shared by app + mock)."
  type        = number
  default     = 2048
}

variable "cache_cep_ttl" {
  description = "Cache TTL for CEP lookups (Spring duration, e.g. 24h)."
  type        = string
  default     = "24h"
}

# --- Data stores ---

variable "db_name" {
  description = "Postgres database name."
  type        = string
  default     = "card_address"
}

variable "db_username" {
  description = "Postgres master username."
  type        = string
  default     = "card"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_multi_az" {
  description = "Whether RDS runs Multi-AZ (recommended in prod; off for a cheap demo)."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.micro"
}
