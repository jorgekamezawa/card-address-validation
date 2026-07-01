# Infra AWS (Terraform) — skeleton

Infraestrutura-alvo do serviço em produção, conforme o
[ADR-0003](../../../docs/ADR-0003-deploy-aws.md): **ECS Fargate + RDS Postgres +
ElastiCache Redis + ALB**, com config/segredos no Secrets Manager e logs/métricas
no CloudWatch.

> **Status: skeleton.** Estrutura versionada para guiar o deploy; **ainda não
> aplicada** (`terraform apply` não foi rodado). Precisa de credenciais AWS, de um
> backend de state remoto e dos ids da VPC/subnets do time de plataforma.

## O que já está modelado

| Arquivo | Recursos |
|---|---|
| `versions.tf` | Terraform + providers (aws, random) |
| `variables.tf` | região, rede, imagem, tamanhos, TTL, provider real |
| `main.tf` | ECR, senha do DB (gerada → Secrets Manager) |
| `data-stores.tf` | Security groups, RDS Postgres (Multi-AZ), ElastiCache Redis |
| `ecs.tf` | ALB + target group, cluster ECS, task definition, service, IAM, logs |
| `outputs.tf` | DNS do ALB, URL do ECR, endpoints de RDS/Redis |

As variáveis de ambiente da task definition são exatamente as `${VAR}` que a app já
lê (12-factor), com `SPRING_PROFILES_ACTIVE=prod` (log JSON) e `DB_PASSWORD` vindo
do Secrets Manager.

## Antes de aplicar (TODO)

- Configurar um **backend remoto** de state (S3 + DynamoDB lock) em `versions.tf`.
- Fornecer `vpc_id`, `private_subnet_ids`, `public_subnet_ids` (VPC existente).
- Criar/− informar o **ACM certificate** e habilitar o `aws_lb_listener` HTTPS
  (comentado em `ecs.tf`).
- Publicar a imagem no ECR e passar `container_image` e `provider_cep_base_url`.

## Uso (quando as credenciais/valores estiverem prontos)

```bash
cd deploy/aws/terraform
terraform init
terraform plan  -var-file=prod.tfvars
terraform apply -var-file=prod.tfvars
```

O `prod.tfvars` (com ids de rede e URLs) **não** é versionado — é config de ambiente.
