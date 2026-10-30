# Issues e pull requests

As entregas são organizadas em cinco issues e cinco PRs encadeados. Cada PR usa o anterior como base para manter a revisão focada; a integração deve seguir a ordem 1 → 5. Os cenários abaixo documentam requisitos e falhas evitadas, não incidentes de produção.

| Ordem | Issue | Pull request |
| --- | --- | --- |
| 1 — Core | [#1](https://github.com/Marcosdovilio/CoopLedger/issues/1) | [#6](https://github.com/Marcosdovilio/CoopLedger/pull/6) |
| 2 — Consistência e acesso | [#2](https://github.com/Marcosdovilio/CoopLedger/issues/2) | [#7](https://github.com/Marcosdovilio/CoopLedger/pull/7) |
| 3 — Eventos | [#3](https://github.com/Marcosdovilio/CoopLedger/issues/3) | [#8](https://github.com/Marcosdovilio/CoopLedger/pull/8) |
| 4 — Operação | [#4](https://github.com/Marcosdovilio/CoopLedger/issues/4) | [#9](https://github.com/Marcosdovilio/CoopLedger/pull/9) |
| 5 — Apresentação | [#5](https://github.com/Marcosdovilio/CoopLedger/issues/5) | [#10](https://github.com/Marcosdovilio/CoopLedger/pull/10) |

Após integrar cada PR, ajuste a base do seguinte para main antes de integrá-lo. As issues são ligadas por Closes nas descrições dos PRs. Nenhum merge foi executado automaticamente.

| Entrega | Reprodução / risco | Implementação e evidência |
| --- | --- | --- |
| Core transacional | Transferir R$ 30 de R$ 100 para zero; uma falha não pode deixar débito isolado | Contas, razão, transferências, outbox e conciliação; migrations e constraints diferidas |
| Consistência e acesso | Reenviar a requisição, disputar o mesmo saldo, consultar conta alheia ou repetir após remoção do perfil OPS | CoreIntegrationIT e MoneyTest/AccountTest; locks ordenados, chave por usuário, validação de autorização inclusive no replay |
| Integração por eventos | Broker pausado e evento entregue duas vezes; falha no insert da projeção | Outbox com retry, consumidor deduplicado, OperationsIntegrationIT com Kafka e PostgreSQL reais |
| Operação e qualidade | Iniciar todo o ambiente do zero, inspecionar erros e backlog de publicação | Compose, healthchecks, Prometheus, Grafana, alertas, CI verify com relatórios |
| Apresentação | Reproduzir fluxo HTTP e explicar decisões e limitações | README, ADRs e scripts de demonstração |

## Regressões demonstráveis

- **Duplicação ao repetir a chave:** sameRequestReplaysAndDifferentPayloadConflicts e simultaneousSameKeyMovesMoneyOnce.
- **Gasto concorrente acima do saldo:** concurrentSpendingApprovesOnlyOneEightyReaisTransfer.
- **Débito sem crédito após falha:** failureAfterFlushedDebitRollsBackEverything.
- **Acesso a outro associado:** apiValidatesBearerOwnershipAmountsAndPagination e historyIsVisibleOnlyToParticipantsAndOperations.
- **Replay após perda do papel de operações:** replayRechecksAuthorizationAfterOperationsRoleIsRemoved.
- **Evento perdido com Kafka fora:** pausedKafkaDoesNotPreventTransferAndOutboxRecovers.
- **Consumo duplicado ou falha parcial:** duplicateKafkaDeliveryCreatesOneProjection, concurrentDuplicateConsumersUseDatabaseUniqueness e projectionFailureAlsoRollsBackDeduplicationAndCanRetry.
- **Divergência de saldo:** reconciliationRecordsDivergenceAndDoesNotChangeBalance.

Cada descrição de PR deve informar o cenário, o teste que o comprova, a decisão aplicada e os limites relevantes. Não é necessário introduzir deliberadamente uma versão financeira incorreta para fabricar histórico.
