# ADR-0002: Observabilidade enxuta embutida na aplicação, com evolução mapeada

- **Status:** Aceito
- **Autor:** Jorge Kamezawa
- **Data:** 2026-06-30

## Resumo (Y-statement)

No contexto de instrumentar o serviço de validação de endereço para visibilidade operacional, sob uma janela de entrega curta, diante da escolha entre uma observabilidade enxuta embutida na aplicação e uma stack dedicada completa de coleta de métricas, dashboards e alertas, decidimos por **endpoints operacionais (Actuator) + fachada de métricas neutra (Micrometer) + log estruturado, com métricas customizadas significativas**, e contra **montar a stack dedicada completa agora**, para ter visibilidade real do que importa dentro do prazo, aceitando que a stack dedicada é **evolução planejada**, e não parte da entrega atual.

## Contexto

Observabilidade é um requisito não-funcional transversal: a instrumentação se desenha *dentro* do fluxo, não se acopla depois. Por isso é decidida agora, mesmo que a entrega seja enxuta — bolt-on posterior sai torto.

O serviço tem poucas coisas que realmente valem a pena enxergar: se as consultas têm sucesso, como o provedor externo se comporta (latência, erros) e se o cache está fazendo o trabalho dele.

Forças em tensão:

- A visibilidade precisa ser real — um endpoint de health "de enfeite" não é observabilidade.
- A janela é curta: uma stack completa de coleta + dashboards + alertas é mais peça para subir e para demonstrar do que o prazo recompensa.
- A superfície operacional deve permanecer pequena, sem abrir mão do que de fato importa monitorar.

## Decisão

Expor saúde e métricas pelos endpoints operacionais do framework (Actuator).

Instrumentar através de uma fachada de métricas neutra de fornecedor (Micrometer): a aplicação é instrumentada **uma vez** e pode exportar para qualquer backend depois — a instrumentação não se prende a uma ferramenta específica. Trocar ou adicionar o backend de métricas mais tarde é configuração, não reinstrumentação.

Emitir logs de aplicação **estruturados** (JSON), legíveis por máquina.

Definir métricas customizadas escolhidas pelo que este serviço de fato precisa monitorar: consultas por desfecho (encontrado / não-encontrado / erro-do-provedor), latência das chamadas ao provedor externo e taxa de acerto/erro do cache (*hit/miss*). São esses números — não um contador genérico — que contam a saúde real do serviço.

## Alternativas consideradas

### Stack dedicada completa agora — Prometheus + Grafana + alertas (adiada)

Coleta de métricas dedicada, dashboards de tendência e alertas por limiar, montados já nesta entrega.

- **Prós:** dashboards "de produção", alertas, o momento visual de impacto.
- **Contras:** mais peças para subir e para demonstrar numa janela curta; o valor (tendência ao longo do tempo, alerta por limiar) exige dados acumulados e setup operacional que o prazo não recompensa. **Adiar não custa nada agora**, porque a instrumentação via Micrometer já exporta no formato que essa stack consome — adotá-la depois é ligar fios, não reinstrumentar. Por isso é *adiada*, não rejeitada: é a evolução natural, não um caminho descartado.

### Métrica mínima / só um health endpoint (rejeitada)

- **Prós:** custo quase zero.
- **Contras:** um health "de enfeite" não é observabilidade. A latência do provedor e a eficácia do cache são justamente o que vale enxergar, e são baratas de adicionar pela fachada. Cortar isso economizaria quase nada e esvaziaria o objetivo.

## Consequências

- (+) Visibilidade real do que importa (comportamento do provedor, mix de desfechos, eficácia do cache) com superfície operacional mínima.
- (+) Instrumentação neutra de fornecedor: a aplicação é instrumentada uma vez; exportar para um backend dedicado depois é configuração, não reinstrumentação.
- (+) Logs estruturados são legíveis por máquina desde o primeiro dia.
- A trilha de evolução está mapeada: métricas customizadas (agora) → coleta em Prometheus + dashboards no Grafana → alertas por limiar → SLOs. Quando uma fase for implementada **conforme planejado aqui**, este ADR recebe uma **nota datada**; se a implementação **divergir materialmente** do que está mapeado, um **novo ADR substitui** este. Implementar a evolução prevista é este ADR se cumprindo — não uma depreciação.
- (−) Não há dashboards nem alertas na entrega atual; tendências e alertas por limiar só existem a partir da fase de evolução.
- (~) O log de auditoria das consultas (horário + payload no banco) é um **requisito funcional** e é coisa separada desta observabilidade operacional — mora na Spec da feature, não aqui.
