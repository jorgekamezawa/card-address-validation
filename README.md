# card-address-validation

![CI](https://github.com/jorgekamezawa/card-address-validation/actions/workflows/ci.yml/badge.svg)

Serviço que, dado um **CEP**, consulta o endereço num **provedor externo**,
**cacheia** o resultado e **persiste um log de auditoria** de toda consulta
(horário + retorno cru do provedor). A moldura é o onboarding de cartão de um
banco; o núcleo é a consulta de CEP com log em banco.

> Desafio técnico. O **desenho da solução** (diagramas) está em
> [`docs/desenho-solucao.md`](docs/desenho-solucao.md); as decisões de arquitetura
> nos [ADRs](docs/) e o comportamento na [Spec](docs/spec-validacao-cep.md).

## Arquitetura

**Ports & adapters "light"** (hexagonal enxuto): a regra da dependência aponta pra
dentro; as fronteiras externas (provedor, cache, persistência) ficam atrás de
**portas** que o domínio define e os adapters implementam. Detalhe e diagramas em
[`docs/desenho-solucao.md`](docs/desenho-solucao.md) · [ADR-0001](docs/ADR-0001-arquitetura.md).

```
domain/            modelo + portas (AddressProvider, AddressCache, CepLookupLogRepository)
application/       AddressLookupService — orquestra (normaliza, cache-aside, audita)
adapter/in/web/    Controller, Mapper, tratamento de erro (ApiError)
adapter/out/provider/     HTTP Interface p/ o provedor + circuit breaker
adapter/out/cache/        Redis via CacheManager
adapter/out/persistence/  JPA + coluna jsonb (auditoria)
observability/     métricas Micrometer
```

## Stack

Java 21 · Spring Boot 3.5 · Postgres (auditoria + `jsonb`) · Redis (cache) ·
Resilience4j (circuit breaker) · WireMock (provedor mock) · Testcontainers ·
Actuator + Micrometer/Prometheus · Gradle · Docker Compose.

## Pré-requisitos

- **Java 21**
- **Docker** + Docker Compose (para Postgres, Redis, mock — e para os testes, que
  usam Testcontainers)

## Como rodar

```bash
# 1. sobe Postgres, Redis e o provedor mock (WireMock)
docker compose up -d

# 2. sobe a aplicação no profile local (logs legíveis no console)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

A app fica em `http://localhost:8080`. Requisições prontas em
[`requests.http`](requests.http). Ao terminar: `docker compose down`.

## Endpoint

`GET /addresses/{cep}` — aceita CEP com ou sem máscara (`01001000` ou `01001-000`).

```bash
curl http://localhost:8080/addresses/01001000
```
```json
{
  "cep": "01001-000",
  "logradouro": "Praça da Sé",
  "complemento": "lado ímpar",
  "bairro": "Sé",
  "cidade": "São Paulo",
  "uf": "SP"
}
```

| Situação | HTTP | `error` |
|---|---|---|
| Encontrado | 200 | — |
| CEP não encontrado | 404 | `CEP_NOT_FOUND` |
| Formato inválido (≠ 8 dígitos) | 422 | `INVALID_CEP_FORMAT` |
| Provedor indisponível / circuito aberto | 503 | `PROVIDER_UNAVAILABLE` |
| Erro inesperado | 500 | `INTERNAL_ERROR` |

Falhas do provedor retornam corpo **opaco** — nenhum nome de provedor, causa ou
stack vaza; o detalhe fica no log interno.

## Auditoria (log das consultas)

Requisito do desafio: **toda consulta que alcança o cache ou o provedor é gravada
no Postgres** com o horário (`queried_at`) e o retorno da API (payload cru na coluna
`jsonb`). Consulta de formato inválido **não** é auditada — é barrada antes de
qualquer integração (não houve "consulta" ao provedor).

Não há endpoint para ler a auditoria (log interno não é feature de produto). Para
inspecionar, consulte a tabela direto — útil na apresentação, logo após rodar
alguns `GET /addresses/...`:

```bash
docker compose exec postgres psql -U card -d card_address \
  -c "SELECT queried_at, cep, outcome, source, response_payload \
      FROM cep_lookup_log ORDER BY queried_at DESC LIMIT 10;"
```

```
          queried_at           |   cep    |    outcome     |  source  |               response_payload
-------------------------------+----------+----------------+----------+-----------------------------------------------
 2026-07-01 22:04:24.087+00    | 12345677 | provider_error | provider |
 2026-07-01 22:04:24.039+00    | 12345674 | not_found      | provider |
 2026-07-01 22:04:23.846+00    | 01001000 | found          | provider | {"uf": "SP", "cep": "01001000", "bairro": ...}
```

O teste E2E também verifica isso automaticamente (consulta a tabela e assere a linha).

## Convenção do provedor mock

Para testar todos os desfechos sem CEPs mágicos, o mock responde pelo **último
dígito** do CEP (como os cartões de teste de sandboxes de pagamento):

| Último dígito | Desfecho |
|---|---|
| 0–3 | encontrado (200) |
| 4, 5, 6, 9 | não encontrado (404) |
| 7 | erro do provedor (500) |
| 8 | timeout (dispara o circuit breaker) |

## Observabilidade

- `GET /actuator/health` — inclui o estado do **circuit breaker**
- `GET /actuator/metrics/cep.lookups` — consultas por desfecho
- `GET /actuator/prometheus` — todas as métricas em formato Prometheus

Métricas customizadas: consultas por desfecho (found/not_found/provider_error/
invalid_format), latência do provedor e hit/miss do cache. Em **produção** os logs
saem em **JSON** (profile `prod`); em dev/test, console legível.

## Testes

```bash
./gradlew test          # suíte completa (precisa de Docker p/ Testcontainers)
./gradlew check         # testes + gate de cobertura (JaCoCo, mínimo 80% de linha)
```

Pirâmide (unit → integração → E2E): lógica/orquestração isoladas com dublês;
persistência contra **Postgres real via Testcontainers** (o `jsonb` é específico do
Postgres); fronteira do provedor contra **WireMock**; um E2E do caminho feliz.
Cobertura atual: **~99% linha / 100% branch**.

## Deploy (AWS)

Deploy em **AWS** — ECS Fargate + RDS Postgres + ElastiCache Redis + ALB —
descrito em [ADR-0003](docs/ADR-0003-deploy-aws.md), com o IaC (Terraform) em
[`deploy/aws/terraform/`](deploy/aws/terraform/). Foi **aplicado e validado de
ponta a ponta** na AWS (o WireMock roda como sidecar na task) e depois **destruído
para não gerar custo** — reaplicável a qualquer momento. Como a app é 12-factor e
stateless, a migração é de configuração/empacotamento — o núcleo não muda.

## Documentação

- [Desenho da solução (diagramas)](docs/desenho-solucao.md)
- [ADR-0001 — Arquitetura](docs/ADR-0001-arquitetura.md)
- [ADR-0002 — Observabilidade](docs/ADR-0002-observabilidade.md)
- [ADR-0003 — Deploy AWS](docs/ADR-0003-deploy-aws.md)
- [Spec — Validação de CEP](docs/spec-validacao-cep.md)

## Escopo deliberadamente deixado de fora (YAGNI)

Clean Architecture completa (interactors, MapStruct), DDD tático (agregados/VOs),
segundo banco (MongoDB — o `jsonb` cobre o payload) e cache em dois níveis. As
razões estão nos ADRs — a força que guia é o núcleo certo com costuras baratas,
não estrutura máxima.
