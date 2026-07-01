# Infra AWS (Terraform)

Infraestrutura do serviço em AWS, conforme o
[ADR-0003](../../../docs/ADR-0003-deploy-aws.md): **ECS Fargate + RDS Postgres +
ElastiCache Redis + ALB**, com a senha do banco no Secrets Manager e logs no
CloudWatch.

> **Status: aplicado e validado.** Este Terraform **subiu de verdade** na AWS e o
> serviço respondeu de ponta a ponta (found / not-found / erro / inválido). Depois
> foi **destruído** (`terraform destroy`) para não gerar custo — reaplicável a
> qualquer momento.

Perfil ajustado para um deploy de **demonstração de baixo custo**: VPC default,
RDS single-AZ, Redis de 1 nó, 1 task, e listener **HTTP** (sem domínio/ACM).

## O que sobe

| Arquivo | Recursos |
|---|---|
| `versions.tf` | Terraform + providers (aws, random) |
| `variables.tf` | região, tamanhos, TTL, tag da imagem |
| `main.tf` | VPC default (data source), 2 repos ECR (app + mock), senha do DB → Secrets Manager |
| `data-stores.tf` | Security groups, RDS Postgres, ElastiCache Redis |
| `ecs.tf` | ALB + target group + listener HTTP, cluster ECS, task (app + WireMock sidecar), service, IAM, logs |
| `outputs.tf` | URL do ALB, URLs do ECR, endpoints de RDS/Redis |

A task roda **dois containers**: a app (8080) e um **WireMock com as mappings
embutidas** (8081) como provedor — o app o alcança em `http://localhost:8081`. As
variáveis de ambiente são as mesmas `${VAR}` que a app já lê (12-factor), com
`SPRING_PROFILES_ACTIVE=prod` (log JSON) e `DB_PASSWORD` vindo do Secrets Manager.

## Como aplicar

Pré-requisitos: AWS CLI autenticado (`aws sts get-caller-identity`), Docker, Terraform.

```bash
cd deploy/aws/terraform
terraform init

# 1. cria só os repositórios ECR
terraform apply -auto-approve \
  -target=aws_ecr_repository.app -target=aws_ecr_repository.provider_mock

# 2. build + push das 2 imagens (a partir da raiz do repo)
REG=<account>.dkr.ecr.<region>.amazonaws.com
aws ecr get-login-password | docker login --username AWS --password-stdin $REG
docker build  --platform linux/amd64 -t $REG/card-address-validation:latest .
docker push   $REG/card-address-validation:latest
docker build  --platform linux/amd64 -f deploy/aws/Dockerfile.provider-mock \
              -t $REG/card-address-validation-provider-mock:latest .
docker push   $REG/card-address-validation-provider-mock:latest

# 3. cria o resto (RDS, Redis, ECS, ALB) — leva ~10-15 min
terraform apply -auto-approve

terraform output app_url   # URL pública do serviço
```

## Destruir (parar o custo)

```bash
terraform destroy -auto-approve
```

Sem deletion-protection e com `force_delete` no ECR, destrói limpo em poucos minutos.

## Evolução (produção real)

Para um ambiente permanente (não demo): backend de state remoto (S3 + DynamoDB
lock), subnets privadas + NAT, listener **HTTPS** com ACM, RDS Multi-AZ, Redis com
réplica, e deploy da app via **GitHub Actions** (OIDC → build/push → update do ECS)
em vez de manual.
