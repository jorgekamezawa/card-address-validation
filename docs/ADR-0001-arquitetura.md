# ADR-0001: Arquitetura ports & adapters enxuta (hexagonal-light) isolando integrações externas

- **Status:** Aceito
- **Autor:** Jorge Kamezawa
- **Data:** 2026-06-30

## Resumo (Y-statement)

No contexto de construir um serviço de validação de endereço (consulta de CEP com registro de auditoria) sob uma janela de entrega curta, diante da escolha entre uma stack completa de Clean Architecture + DDD tático e uma organização leve de ports & adapters, decidimos por **ports & adapters "light" (hexagonal enxuto)** — domínio no centro, integrações externas alcançadas por portas que o núcleo define e adapters implementam —, e contra **Clean Architecture + DDD tático** e **um segundo banco (de documentos)**, para entregar um núcleo focado e evoluível dentro do prazo, aceitando que parte do detalhamento é deliberadamente adiada até que uma necessidade real a justifique.

## Contexto

O serviço precisa validar endereços através de um provedor externo de CEP, persistir um log de auditoria de toda consulta (horário e o payload retornado) e permanecer simples de operar e estender. A janela de entrega é curta, então a força que guia não é "construir tudo bem", e sim **construir o núcleo certo, com as fronteiras posicionadas onde a mudança futura é barata**.

Forças em tensão:

- O domínio é magro — uma consulta mais uma trilha de auditoria. Não há regra de negócio rica, invariante ou agregado a proteger.
- O serviço depende de sistemas que não controlamos (o provedor externo) e de infraestrutura que difere entre local e nuvem (cache, banco). Essas fronteiras são onde a mudança é mais provável.
- Uma janela curta premia a contenção, mas a contenção não pode produzir um núcleo doloroso de evoluir.

## Decisão

Adotar uma organização **ports & adapters enxuta**, com a regra da dependência apontando pra dentro:

- **`domain`** — o modelo (`Address`, enums, normalização) e as **portas** que o núcleo define (`domain/port`): a do provedor (`AddressProvider`) e a do log de auditoria (`CepLookupLogRepository`).
- **`application`** — o caso de uso (`AddressLookupService`) que orquestra "consultar, cachear e logar", dependendo **só das portas**, nunca de implementações concretas.
- **`adapter`** — as implementações nas bordas: `adapter/in/web` (HTTP), `adapter/out/provider` (cliente do provedor) e `adapter/out/persistence` (JPA).

Isso mantém o núcleo independente do detalhe de integração: o provedor pode ser trocado (API real ↔ mock local) e o backend de cache pode mudar (container local ↔ serviço gerenciado) sem tocar na lógica de orquestração, e o caso de uso pode ser exercitado isoladamente contra substitutos. O vocabulário do domínio segue o contexto de onboarding de cartão que o serviço atende (linguagem ubíqua), mas **nenhum construto de DDD tático** é introduzido — não há invariante aqui que justifique um agregado ou um value object.

O hexagonal é mantido **enxuto, não de manual**: um único modelo (sem espelhar domínio e persistência em duas hierarquias), o mapeamento domínio↔entity é **inline e trivial no adapter** (sem classes de mapper dedicadas nem MapStruct), e sem use-case interactors. A entity de persistência usa o sufixo `JpaEntity` porque a fronteira é explícita e ela é um detalhe do adapter, não o modelo de domínio.

**Persistência:** um único banco relacional (Postgres). O payload retornado pelo provedor é guardado numa coluna `jsonb`, o que cobre a necessidade de "guardar o que a API retornou" sem um segundo banco.

**Cache:** as consultas são cacheadas (Redis), atrás da abstração de cache do Spring, para proteger o provedor externo de chamadas repetidas. O dado CEP→endereço é altamente cacheável (muda raramente), então o cache corta latência e carga sobre o provedor já a partir da primeira repetição.

A topologia concreta de deploy (compute, e onde os bancos rodam) fica intencionalmente fora de escopo aqui: depende de fatos conhecidos só quando o deploy for de fato realizado e não afeta o código, então é adiada para o seu próprio ADR naquele momento.

## Alternativas consideradas

### Clean Architecture + DDD tático (rejeitada)

Use-case interactors completos, fronteiras de módulo com inversão de dependência por toda parte, agregados, value objects, mappers dedicados entre modelo de domínio e de persistência.

- **Prós:** isolamento máximo; forma "de empresa" forte.
- **Contras:** cerimônia que um serviço de "consultar e logar" não paga. Sem um domínio rico para modelar, esses construtos adicionam indireção sem proteger nada — complexidade construída para um futuro presumido em vez de uma necessidade presente. O custo chega agora; o benefício nunca chega nesta escala. O ports & adapters enxuto fica com o ganho que importa (núcleo isolado das integrações, testável) sem a cerimônia.

### Segundo banco para o payload — MongoDB (rejeitada)

Um banco de documentos ao lado do relacional para o payload semiestruturado.

- **Prós:** documento é cidadão de primeira classe nele; consulta mais rica sobre atributos aninhados do payload (agregação, filtros complexos sobre a estrutura do documento) do que o `jsonb` oferece em escala.
- **Contras:** essa consulta mais rica é justamente o que este serviço *não* precisa — o payload é gravado e relido para auditoria, nunca consultado analiticamente. Um segundo banco adiciona uma peça operacional (e um ponto a mais que pode falhar numa demo ao vivo) sem trabalho atual, enquanto o `jsonb` cobre gravar-e-reler nativamente. *Se* a consulta rica sobre o conteúdo do payload virar requisito depois, reavaliar a persistência de documento é uma decisão futura (seu próprio ADR), não uma necessidade hoje.

### Cache em memória (in-process) em vez de Redis (considerada)

- **Prós:** zero infraestrutura extra; trivial.
- **Contras:** não sobrevive a um restart e não é compartilhado entre instâncias, perdendo a forma de cache distribuído que reflete como esse tipo de serviço roda em produção. O Redis carrega um trabalho real (proteger o provedor) sem duplicar nenhum outro componente, então a peça extra se justifica — diferente do banco rejeitado.

## Consequências

- (+) O núcleo permanece independente do detalhe de integração e de infraestrutura; os adapters de provedor, persistência e cache mudam atrás de suas portas sem mudar o núcleo, e o caso de uso é testável contra substitutos.
- (+) As costuras ficam **nomeadas e explícitas** (as portas), exatamente onde a mudança é mais provável (provedor externo, persistência, cache, deploy), então o detalhamento adiado agora permanece barato de acrescentar depois. O adiamento é deliberado, com o caminho de evolução mapeado — não é dívida escondida.
- (+) Um único banco relacional cobre tanto o log de auditoria quanto o payload — menos peças para operar e para demonstrar.
- (−) Há um pouco mais de indireção que um desenho em camadas puro (a persistência também fica atrás de uma porta + adapter), aceita conscientemente em troca de clareza das fronteiras e testabilidade do núcleo.
- (−) O desenho é hexagonal **enxuto** de propósito: não exibe a estrutura completa de Clean Architecture / DDD (interactors, mappers dedicados, modelos separados). É uma escolha consciente de escopo para este domínio e prazo, não um esquecimento.
- (−) O `jsonb` é suficiente agora, mas seria a ferramenta errada se a consulta analítica sobre o conteúdo do payload algum dia virasse central — uma fronteira que aceitamos e revisitaríamos explicitamente.
- (~) Cache distribuído (Redis) faz parte desta decisão; a topologia concreta de deploy não — ela é registrada à parte, em seu próprio ADR, quando o deploy for realizado.
