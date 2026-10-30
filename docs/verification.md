# Evidências de validação — 30/10/2026

## Build e testes locais

Com JDK 21 e Docker Desktop, `./mvnw -B verify` concluiu com BUILD SUCCESS:

| Suíte | Testes | Falhas / erros / ignorados |
| --- | ---: | --- |
| MoneyTest | 7 | 0 / 0 / 0 |
| AccountTest | 5 | 0 / 0 / 0 |
| CoreIntegrationIT | 14 | 0 / 0 / 0 |
| OperationsIntegrationIT | 5 | 0 / 0 / 0 |
| Total | 31 | 0 / 0 / 0 |

Os testes de integração criaram PostgreSQL 17.7 e Kafka 3.9.1 reais. Incluem pausa do broker, concorrência, rollback, idempotência, autorização e deduplicação com falha de persistência.

## Ambiente Compose e APIs

`docker compose --profile monitoring up --build -d --wait` concluiu. Core, operações, PostgreSQL e Kafka ficaram saudáveis. As especificações OpenAPI das duas APIs responderam.

`python3 scripts/demo.py` comprovou a transferência inicial de R$ 30: Alice R$ 100 → R$ 70; repetição com o mesmo ID sem novo débito; conflito 409 para payload diferente; acesso a extrato alheio 403; conciliação com duas contas e zero divergências; consulta da projeção em operações.

Depois, com Kafka desligado pelo Compose, uma transferência de R$ 10 confirmou: Alice R$ 70 → R$ 60 e um evento pendente. Ao religar o broker e repetir a mesma chave, a API retornou 200 sem novo débito e a projeção ficou disponível. Ao término da demonstração, os saldos locais eram R$ 60/R$ 40.

O Prometheus apresentou os dois targets como up, métricas de transferências por resultado e quantidade/idade da outbox. O Grafana respondeu com o dashboard provisionado e seis painéis.

## GitHub

Foram publicadas cinco issues e cinco PRs encadeados, ligados em [issues.md](issues.md). Os PRs permanecem abertos para revisão; nenhum merge foi executado.

A pipeline está versionada com gatilhos push, pull_request e workflow_dispatch. A configuração consultada do repositório indicava Actions habilitado, mas as APIs de runs/checks ainda retornavam zero execuções na verificação desta entrega. Portanto, os 31 testes acima são uma validação local; não há afirmação de aprovação em runner remoto. A execução remota deve ser confirmada ao integrar a pipeline e, então, pode também ser disparada manualmente.
