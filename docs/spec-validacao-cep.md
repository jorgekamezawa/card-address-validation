# Spec: Validação de endereço por CEP

> Fonte da verdade desta feature. Detalha o que construir e como saber que terminou. Não re-justifica decisões que vivem nos ADRs — referencia.

## Referências

- Desenho de solução: `README` (estado atual) — diagrama e pitch.
- ADRs (restrições já decididas, tratadas como dadas): **ADR-0001** (ports & adapters enxuto / hexagonal-light; `jsonb`; Redis; sem Mongo/DDD/Clean-Arch de manual) e **ADR-0002** (observabilidade enxuta: Actuator + Micrometer + log estruturado).

## Visão geral

Dado um CEP, o serviço consulta o endereço num provedor externo, devolve um endereço enxuto ao chamador, e registra em banco uma trilha de auditoria de toda consulta que alcança o provedor ou o cache. As consultas bem-sucedidas são cacheadas para proteger o provedor externo. É a fatia ponta a ponta no contexto de onboarding de cartão (validação de endereço); a moldura de cartão é narrativa — não há regra de elegibilidade.

## História

Como sistema de onboarding de cartão, quero validar e enriquecer o endereço de um cliente a partir do CEP, para preencher o endereço de forma confiável e manter um registro auditável de cada consulta feita ao provedor de CEP.

## Critérios de aceite (EARS)

- **Normalização (ubíquo):** O sistema DEVE normalizar o CEP recebido removendo tudo que não é dígito antes de validar ou consultar.
- **Validação de formato (comportamento indesejado):** SE o CEP normalizado não tiver exatamente 8 dígitos, ENTÃO o sistema DEVE responder com erro explícito de formato inválido, **sem** chamar o provedor e **sem** gravar auditoria.
- **Consulta bem-sucedida via provedor (evento):** QUANDO um CEP válido é consultado e não está em cache, o sistema DEVE chamar o provedor, e SE o provedor retornar o endereço, ENTÃO DEVE responder com o endereço, gravar a consulta em cache, e gravar auditoria com `outcome = found` e `source = provider`.
- **Consulta servida do cache (dirigido a estado):** ENQUANTO um CEP válido já estiver em cache, o sistema DEVE responder com o endereço **sem** chamar o provedor, e DEVE gravar auditoria com `outcome = found` e `source = cache`.
- **CEP não encontrado (evento):** QUANDO um CEP válido é consultado e o provedor indica que não existe, o sistema DEVE responder com erro explícito de não-encontrado e gravar auditoria com `outcome = not_found` e `source = provider`.
- **Provedor indisponível (comportamento indesejado):** SE o provedor estiver indisponível, exceder timeout, ou retornar erro, ENTÃO o sistema DEVE responder com um erro **opaco** (sem expor detalhe do provedor, causa ou stack) e gravar auditoria com `outcome = provider_error` e `source = provider`.
- **CEP formatado na saída (ubíquo):** O sistema DEVE retornar o CEP no corpo da resposta no formato `00000-000`, independentemente de a entrada ter vindo com ou sem máscara.
- **Auditoria de toda consulta que integra (ubíquo):** O sistema DEVE gravar uma linha de auditoria com o horário da consulta e o payload retornado para toda consulta que alcança o cache ou o provedor (found, not_found, provider_error). A linha de formato inválido **não** é gravada (não houve integração).
- **Métrica de desfecho (ubíquo):** O sistema DEVE expor métricas de consultas por desfecho (incluindo as rejeitadas por formato inválido), de latência da chamada ao provedor, e de acerto/erro do cache.

### Exemplos (cada linha vira um caso de teste)

| Caso | Pré-condição (estado) | Entrada | Resultado esperado |
|------|-----------------------|---------|--------------------|
| Encontrado, com máscara | não está em cache | `01001-000` | 200; endereço; auditoria `found`/`provider`; gravado em cache |
| Encontrado, sem máscara | não está em cache | `01001000` | 200; mesmo endereço; CEP na resposta sai `01001-000` |
| Encontrado via cache | já está em cache | `01001000` | 200; endereço; **provedor não chamado**; auditoria `found`/`cache` |
| Não encontrado | — | `99999999` | 404 explícito; auditoria `not_found`/`provider` |
| Formato inválido (curto) | — | `123` | 422 explícito; **provedor não chamado**; **sem auditoria**; métrica de inválido +1 |
| Formato inválido (letras) | — | `abcde-fgh` | 422 explícito; sem auditoria |
| Provedor fora do ar | provedor indisponível | `01001000` | 5xx **opaco**; auditoria `provider_error`/`provider` |
| Provedor com timeout | provedor lento | `01001000` | 5xx opaco; auditoria `provider_error`/`provider` |

## Dependências / pré-requisitos

- Mock do provedor (WireMock) configurado com respostas de referência (encontrado, não-encontrado, erro/timeout). O contrato do mock é **enxuto — só os campos que o domínio usa** (`logradouro`, `complemento`, `bairro`, `localidade`, `uf`, `cep`); o formato de um provedor real de CEP serviu apenas de referência, não é replicado. Mesmo mock serve runtime e testes de integração.
- Postgres e Redis disponíveis via Docker Compose (estado atual).

## Design / abordagem

Ports & adapters conforme ADR-0001, com a regra da dependência apontando pra dentro:

- **Adapter de entrada — HTTP (`adapter/in/web`):** recebe o CEP, chama o caso de uso, devolve o DTO de resposta. Sem lógica de orquestração ou integração. O **mapper manual** (modelo de domínio → DTO de resposta) vive nesta borda e é onde a **formatação do CEP** (`00000-000`) é aplicada.
- **Aplicação (service / caso de uso):** orquestra o fluxo **cache-aside** — tenta o cache; em miss, chama o provedor pela porta, popula o cache no sucesso; grava auditoria; classifica o desfecho. Trabalha com o CEP **canônico** (só dígitos). Depende só das portas (provedor, log de auditoria) e da abstração de cache, não de implementações.
- **Porta do provedor + adapter de saída (`adapter/out/provider`):** o provedor externo é alcançado por uma porta que o núcleo define e o adapter implementa, via **HTTP Interface (`@HttpExchange`)** sobre RestClient. O adapter traduz a resposta crua no modelo de domínio e guarda o corpo bruto; o mock sinaliza **não-encontrado com HTTP 404** (adapter → `Optional.empty`), e falha/timeout/resposta inválida viram `ProviderUnavailableException`. Trocar provedor real ↔ mock não toca o núcleo.
- **Porta de cache + adapter de saída (`adapter/out/cache`):** o resultado "encontrado" é cacheado via a **abstração de Cache do Spring** (`CacheManager`/`Cache`), com Redis como backend plugável, atrás de uma porta `AddressCache`. Não se usa `@Cacheable` (a anotação não expõe hit/miss, e o service precisa saber a origem para auditar `found/cache` vs `found/provider`); a mecânica de cache-aside fica isolada no adapter, não espalhada no service. Indisponibilidade do cache não derruba a consulta (get falho = miss, put falho = ignorado).
- **Porta de auditoria + adapter de saída (`adapter/out/persistence`):** o caso de uso grava cada consulta pela porta `CepLookupLogRepository`; o adapter JPA persiste a linha, com o payload retornado num campo `jsonb`.
- **Tratamento de erro:** um `@RestControllerAdvice` central + **exceptions customizadas** traduzem cada desfecho de falha no response correto — explícito para erro de input/cliente (formato inválido, não-encontrado), **opaco** para falha de provedor (infosec).

## Contratos

Endpoint (forma estável; detalhe de implementação à parte):

**Requisição:** `GET /addresses/{cep}` — `{cep}` aceita com ou sem máscara.

**Resposta 200 (encontrado):**

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

**Respostas de erro** (corpo padronizado pelo handler):

- **422** — formato inválido: corpo **explícito** (mensagem clara de que o CEP é inválido).
- **404** — não encontrado: corpo **explícito** (CEP não localizado).
- **5xx** — falha de provedor: corpo **opaco** (mensagem genérica de indisponibilidade temporária; sem detalhe do provedor/causa).

Campos de endereço no vocabulário de CEP (PT): `cep`, `logradouro`, `complemento`, `bairro`, `cidade`, `uf`. O código ao redor (classes, métodos) é em inglês.

## Modelo de dados

Tabela `cep_lookup_log` (trilha de auditoria; append-only):

- `id` — identificador da linha.
- `cep` — CEP consultado, canônico (só dígitos).
- `queried_at` — horário da consulta (requisito explícito do desafio).
- `outcome` — `found` | `not_found` | `provider_error`.
- `source` — `provider` | `cache` (qual atendeu a consulta).
- `response_payload` — **`jsonb`**: a resposta crua do provedor como veio (nulo quando não houve resposta — ex.: `provider_error` sem corpo). Guardar o retorno **verbatim** (não uma re-serialização do subconjunto mapeado) preserva fidelidade ao que o provedor respondeu e sobrevive a o provedor passar a devolver campos novos sem migração; o contrato de saída é enxuto, a auditoria guarda o retorno cru.

Mecanismo: o `jsonb` cobre o payload semiestruturado sem segundo datastore (ADR-0001). Sem índice especial nesta fase (não há consulta analítica sobre o payload — ADR-0001).

## Decisões de refinamento

- [2026-06-30] CEP canônico (só dígitos) no domínio; formatação `00000-000` aplicada no mapper da borda — separa apresentação de negócio (SRP); o domínio não sabe como o CEP é exibido.
- [2026-06-30] Formato inválido **não** gera auditoria — auditoria registra integração com o provedor; formato inválido é barrado antes de qualquer integração. Visibilidade de tentativas inválidas fica por conta de **métrica**, não do audit log.
- [2026-06-30] Cache de um nível só (Redis), não L1+L2 (Caffeine+Redis) — o ganho de L1 (hot tail de alta frequência) não existe aqui e o custo de consistência entre níveis não se paga. Seam preservado: trocar o `CacheManager` por baixo não toca o service.
- [2026-06-30] Mapper manual em vez de MapStruct — um mapeamento único e trivial não paga o gerador; troca futura é barata.

## NFRs

- **Observabilidade (ADR-0002):** Actuator (health/metrics), Micrometer com as métricas customizadas (desfecho, latência do provedor, hit/miss do cache), log estruturado (JSON). A auditoria em banco é requisito funcional, separada destas métricas.
- **Segurança da informação:** falha de provedor retorna resposta **opaca** — não vaza nome do provedor, causa, stack ou detalhe interno. O detalhe vive só no log/auditoria internos.
- **Cache:** TTL configurável por variável de ambiente (12-factor); apenas o desfecho "encontrado" é cacheado (não cacheia not_found nem erro).
- **Configuração:** toda config (URL do provedor, TTL, conexões) vem do ambiente; segredos fora do código.
- **Resiliência:** indisponibilidade do cache não derruba a consulta — o serviço cai pro provedor (cache é otimização, não fonte da verdade).

## Fora de escopo

- Regra de elegibilidade / emissão de cartão (a moldura de cartão é narrativa).
- Cache em dois níveis (L1 Caffeine + L2 Redis).
- Segundo datastore (MongoDB) — `jsonb` cobre o payload.
- Topologia de deploy (compute, onde os bancos rodam) — decisão futura, ADR próprio (ADR-0001).
- Busca por endereço (rua/cidade → CEP); só consulta por CEP.

## Plano de testes

Derivado dos critérios EARS, organizado na pirâmide (ver `CLAUDE.md`). **Os critérios EARS e a tabela de exemplos são o conjunto mínimo que deve ter teste; a implementação cobre também as variações de borda de cada comportamento (ex.: outras formas de CEP inválido — vazio, 9 dígitos, só máscara) na camada apropriada.**

- **Unit (base):** normalização do CEP (com/sem máscara, dígitos insuficientes, com letras); classificação de desfecho na orquestração com a porta do provedor, o cache e o repositório substituídos por dublês; o mapper manual (incluindo a formatação do CEP na saída). Rápidos, sem Spring, sem rede, sem banco.
- **Integração (meio):**
  - Persistência da auditoria contra **Postgres real via Testcontainers** (não H2 — o `jsonb` é específico do Postgres).
  - Fronteira do provedor contra **WireMock** (mesmo mock do runtime): encontrado, não-encontrado, erro e timeout — exercita o adapter e o tratamento de erro/opacidade.
- **E2E (topo, mínimo):** o fluxo crítico ponta a ponta pela aplicação rodando — `GET` de um CEP encontrado → resposta correta + linha de auditoria gravada. Só o caminho feliz; casos de erro ficam nas camadas de baixo.

Regras: pegar cada bug na camada mais barata; gerenciar estado por teste (isolamento); AAA; nunca enfraquecer/apagar teste pra passar.

## Diagrama de sequência (estado atual)

```
Cliente → Controller → Service (caso de uso)
                          │
                          ├─ Cache (Redis) ── hit ─► retorna endereço ─► (audita found/cache)
                          │        │
                          │       miss
                          │        ▼
                          ├─ Porta do provedor → Adapter → Provedor externo (mock/real)
                          │        │
                          │   found ─► popula cache ─► (audita found/provider)
                          │   not_found ─► (audita not_found/provider) ─► 404 explícito
                          │   erro/timeout ─► (audita provider_error) ─► 5xx opaco
                          ▼
                    Mapper (formata CEP) → DTO de resposta → Cliente
```
