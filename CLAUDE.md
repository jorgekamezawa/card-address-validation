# CLAUDE.md — card-address-validation

Serviço de validação de endereço para um contexto de onboarding de cartão: dado um CEP, consulta o endereço num provedor externo, cacheia o resultado e persiste um log de auditoria de toda consulta. Construído sob uma janela de entrega curta — a força que guia é o núcleo certo com costuras onde a mudança futura é barata, não estrutura máxima. As decisões vivem nos ADRs (`docs/`); este arquivo é o contrato de trabalho que a implementação segue.

## Idioma

- **Código sempre em inglês:** identificadores, comentários, nomes de teste, mensagens de commit, nomes de branch e descrições de PR.
- **Documentação em português:** ADRs, Spec, README, desenho de solução (este repositório é voltado a avaliação no Brasil).
- Commits seguem **Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`…), em inglês.

## Stack

- **Java 21** (LTS).
- **Spring Boot 3.x** — web, validação, Spring Data JPA.
- **Postgres** — único banco relacional: log de auditoria + payload retornado numa coluna `jsonb` (ADR-0001).
- **Redis** — cache das consultas de CEP, para proteger o provedor externo (ADR-0001). Abstraído atrás do suporte a cache do Spring.
- **WireMock** — substituto do provedor externo de CEP, rodando como container; o mesmo mock serve runtime e testes de integração.
- **Docker / Docker Compose** — Postgres, Redis e o mock do provedor sobem com um comando.
- **Observabilidade:** Spring Boot Actuator + Micrometer + log estruturado (JSON) (ADR-0002).
- **Build:** Gradle.
- **Testes:** JUnit 5; test slices do Spring Boot; Testcontainers (Postgres real) para integração; WireMock para a fronteira do provedor. Ver a seção Testes.

## Arquitetura (do ADR-0001 — tratar como decidido)

Organização em **ports & adapters "light"** (hexagonal enxuto — ver Emenda [2026-06-30] no ADR-0001), com a regra da dependência apontando pra dentro. Layout de pacotes: `domain` (modelo + `domain/port`), `application` (caso de uso), `adapter/in/web`, `adapter/out/provider`, `adapter/out/persistence`. Cada camada com uma só razão pra mudar:

- **Borda HTTP** — controllers falam só HTTP: recebem a requisição, devolvem a resposta. Sem lógica de orquestração ou integração aqui.
- **Camada de aplicação** — orquestra o caso de uso (consultar, cachear, auditar). Depende de abstrações, não de clientes concretos.
- **Provedor externo atrás de uma porta** — o provedor é alcançado por uma abstração que o núcleo define e a borda implementa. É o que permite trocar a API real e o mock sem tocar no núcleo, e o que torna o caso de uso testável contra um substituto.
- **Persistência** — o log de auditoria é gravado por um repositório; o payload retornado é guardado como `jsonb`.
- **Cache** — as consultas passam pela abstração de cache; o backend (container local ↔ serviço gerenciado) pode mudar sem tocar no núcleo.

O vocabulário de domínio segue o contexto de onboarding de cartão (linguagem ubíqua: consulta, endereço, auditoria). É o único DDD emprestado aqui.

## O que NÃO construir (explícito — não "ajude" adicionando isto)

Foram considerados e deliberadamente deixados de fora (ADR-0001). Não os introduza:

- **Sem cerimônia de Clean Architecture completa** — sem classes de use-case interactor, sem mappers dedicados (MapStruct); o mapeamento domínio↔entity é inline e trivial no adapter. As camadas ficam leves; a regra que importa é o núcleo não depender da borda.
- **Sem DDD tático** — sem agregados, value objects ou domain events. Não há invariante aqui que os justifique. Uma função ou serviço simples é a ferramenta certa.
- **Sem segundo banco** — sem MongoDB. O `jsonb` cobre o payload semiestruturado. Se a consulta rica sobre o conteúdo do payload algum dia virar requisito, é decisão futura (ADR próprio), não algo a pré-construir.
- **Sem endpoints, métodos ou abstrações especulativos** — ver YAGNI abaixo.

Se uma mudança parecer pedir um destes, pare e sinalize para decisão humana em vez de adicionar.

## Princípios

- **Clean Code em primeiro lugar: legibilidade acima de esperteza.** Os nomes carregam intenção; o nome de um método diz o que ele faz e por quê. Comentários são a exceção rara — ver **Comentários** abaixo.
- **SOLID** aplicado pelo que entrega, não como decoração. Concretamente aqui: cada camada tem uma só razão para mudar (controller = HTTP, aplicação = orquestração, adapter = integração, repositório = persistência); o núcleo depende de abstrações para o provedor e o cache (então são trocáveis e o caso de uso é testável contra fakes); as portas ficam estreitas — só os métodos que a feature atual chama.
- **YAGNI — construa só o que a feature atual precisa.** Um repositório ou porta expõe só os métodos em uso agora, não um CRUD completo "por via das dúvidas". O mesmo para endpoints, serviços e config. Nada "para o futuro" sem necessidade presente.
- **Evoluibilidade por costuras, não por complexidade especulativa.** As fronteiras externas (provedor, cache, deploy) são onde a mudança é mais provável, então ficam atrás de abstrações — isso mantém o detalhamento posterior barato. Isto *não* contradiz YAGNI: não pré-construir features é YAGNI; manter as costuras limpas é design evolutivo.

## Comentários (Clean Code, Cap. 4)

Regra padrão para todo desenvolvimento futuro. O melhor comentário é o que você não precisou escrever porque o código já se explica. Antes de escrever um, tente renomear ou extrair um método.

- **Escreva um comentário só quando ele carrega o *porquê* não-óbvio** — uma decisão, uma restrição externa, um workaround, ou a semântica de um contrato (porta) que a assinatura não expressa. Ex.: "não-transacional de propósito: cada auditoria precisa commitar mesmo se a consulta falhar depois".
- **Nunca comente o *o quê*.** Se o comentário reafirma o nome do tipo/método ou descreve o que o código plainly faz, apague-o — é ruído (o smell *Redundant/Mandated Comments*). Javadoc obrigatório em toda classe é exatamente esse anti-padrão; não faça.
- **Prefira uma linha.** Se o *porquê* cabe numa linha, não gaste um parágrafo. Condense.
- **Sem comentário de posição, chave-fechada, código comentado, ou histórico** — isso é trabalho do git e do nome.
- Vale para código de produção e de teste; o nome do teste é a documentação do comportamento.

## Observabilidade (do ADR-0002)

- Expor saúde e métricas via Actuator; instrumentar via Micrometer (fachada neutra de fornecedor — instrumenta uma vez, exporta para qualquer lugar depois).
- Logs de aplicação estruturados (JSON).
- Métricas customizadas que importam para este serviço: consultas por desfecho (encontrado / não-encontrado / erro-do-provedor), latência da chamada ao provedor externo e taxa de acerto/erro do cache.
- O **log de auditoria** (horário + payload retornado persistido no Postgres) é um **requisito funcional**, separado da observabilidade operacional. É comportamento de feature, especificado na Spec — não uma métrica.

## Testes — a pirâmide de testes

Os testes são **derivados dos critérios de aceite da Spec** (EARS), não escritos no estilo TDD primeiro, e devem passar antes de uma mudança ser considerada concluída. São organizados como uma pirâmide: uma base larga de testes unitários, menos testes de integração, e uma ponta pequena de testes E2E. Forma-alvo aproximada: ~70% unit / ~20% integração / ~10% E2E — um guia, não dogma. Cada camada responde uma pergunta diferente:

- **Unit (base, mais numerosos)** — "esta peça faz o que espero, isolada?" Lógica de negócio/orquestração com os colaboradores externos (porta do provedor, repositório, cache) substituídos por dublês. Rápidos, sem contexto Spring, sem rede, sem banco. Cubra o caminho feliz e os casos de borda aqui — é o lugar mais barato de pegar um bug.
- **Integração (meio, menos)** — "os componentes de fato funcionam juntos?" Os pontos onde a app serializa/deserializa ou cruza uma fronteira: a API REST, o repositório contra o banco, e a chamada ao provedor externo. Duas coisas importam aqui para este projeto:
  - **Persistência contra um Postgres real via Testcontainers — não H2.** O payload de auditoria é guardado como `jsonb`, um tipo específico do Postgres; um substituto em memória (H2) passaria e então mentiria, porque não se comporta como o Postgres. Testes de integração que tocam o banco rodam contra um container Postgres real.
  - **A fronteira do provedor contra o WireMock** — o mesmo substituto usado em runtime — então os testes são determinísticos e não precisam de rede. É aqui que o comportamento de erro/borda do provedor (não-encontrado, provedor fora, resposta malformada) é exercitado.
- **E2E (ponta, os menos)** — "o fluxo inteiro de fato funciona?" Só o caminho crítico, ponta a ponta pela aplicação rodando (requisição → cache → provedor → auditoria → resposta). E2E é reservado para esse fluxo; **não** é onde os casos de erro são enumerados.

**Regras que mantêm a pirâmide saudável:**

- **Pegue cada bug na camada mais barata.** Se um comportamento pode ser verificado num teste unitário, é lá que ele mora, não no E2E. Se vários testes cobrem o mesmo comportamento, empurre-o para baixo na pirâmide.
- **Evite os anti-padrões conhecidos:** o "ice-cream cone" (E2E demais, poucos unit/integração — lento e instável) e o "hourglass" (muitos unit e muitos E2E, mas integração de menos — perde bugs que testes de escopo médio pegam barato). O valor deste serviço vive nas suas fronteiras, então uma camada de integração relativamente robusta é esperada e está ok.
- **Gerencie o estado por teste** (isolamento transacional ou reset explícito) para que os testes de integração/E2E não fiquem dependentes de ordem.
- **Siga Arrange-Act-Assert**; mantenha cada teste focado num comportamento; trate o código de teste como código de produção (legível, mantido).
- **Nunca** apague nem enfraqueça um teste que falha para fazê-lo passar. Conserte o código.

## Convenções

- **Sempre peça aprovação humana antes de qualquer commit.** Mostre o que mudou e espere um "sim" explícito antes de rodar `git commit`. Nunca commite (nem faça push) sem ser solicitado.
- A config vem do ambiente (12-factor); segredos nunca commitados.
- Commits pequenos e descritivos (Conventional Commits).
- Rode os testes antes de declarar uma tarefa concluída.
- Pergunte antes de: adicionar uma dependência nova, uma ação destrutiva (apagar dados/colunas), ou mudar configuração de infra/segurança.

## Notas

- O README é escrito **por último**, contra o código pronto, para ficar fiel ao que de fato existe (endpoints, comandos, estrutura).
- A topologia de deploy (compute, onde os bancos rodam) é intencionalmente indecidida aqui — vira seu próprio ADR quando o deploy for de fato realizado (ADR-0001).
