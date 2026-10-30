# Decisões de arquitetura

## ADR 001 — Uma fronteira transacional para dinheiro

**Decisão:** contas, transferência, lançamentos, reserva de idempotência e evento pendente ficam no mesmo PostgreSQL e na mesma transação JPA/JDBC. O segundo serviço mantém apenas uma projeção.

**Motivo:** débito e crédito precisam confirmar ou reverter juntos. Uma transação distribuída entre bancos e Kafka ampliaria o problema sem benefício neste escopo. O banco de operações pode atrasar sem alterar o saldo verdadeiro.

**Consequência:** operações não autoriza débitos nem representa a fonte de saldo. Uma resposta 404 nesse serviço pode significar atraso de consumo.

## ADR 002 — Bloqueios explícitos e ordenados

**Decisão:** buscar cada conta com lock pessimista, em ordem lexicográfica dos UUIDs, antes de conferir saldo e situação.

**Motivo:** @Transactional sozinho não impede duas requisições de ler o mesmo saldo e gastá-lo. Dois SELECT FOR UPDATE separados tornam a ordem de aquisição explícita.

**Consequência:** transferências sobre a mesma conta são serializadas. Um lock timeout retorna conflito e exige retry com a mesma chave. O teste de saídas concorrentes de R$ 80 sobre R$ 100 exige uma aprovação e saldo final R$ 20. Transferências em sentidos opostos exercitam a ordem de locks.

## ADR 003 — Reserva transacional de idempotência

**Decisão:** chave primária composta pelo subject autenticado e Idempotency-Key. Primeiro, INSERT ON CONFLICT DO NOTHING; se já existir, comparar SHA-256 dos campos normalizados e recuperar a transferência.

**Motivo:** consultar antes de inserir deixa uma corrida entre requisições. A unicidade do PostgreSQL faz a inserção concorrente esperar a transação anterior.

**Consequência:** reservas de operações recusadas também revertem. Esta versão mantém chaves indefinidamente e só persiste operações concluídas. Valores 30 e 30.00 são equivalentes; descrições diferentes geram conflito. Mesmo em replay, a autorização atual é verificada. Datas da transferência são normalizadas para microssegundos, a precisão persistida no PostgreSQL, para a resposta original e a repetida coincidirem.

## ADR 004 — Razão imutável e verificação no commit

**Decisão:** valor positivo em BigDecimal / NUMERIC(19,2), lançamentos DEBIT/CREDIT de mesmo valor e OPENING_CREDIT apenas para a carga controlada. Triggers bloqueiam alteração/exclusão do histórico e validam o par financeiro no commit.

**Motivo:** o extrato deve explicar o saldo, incluindo o saldo inicial. Um teste que prepara R$ 100 sem lançamento inicial criaria uma divergência artificial.

**Consequência:** um estorno deverá gerar outra operação e lançamentos compensatórios. Usuários de banco com privilégios administrativos ainda podem remover triggers; separação entre papel de migração e aplicação é trabalho de implantação de produção. Testes limpam somente seus próprios bancos efêmeros.

## ADR 005 — Outbox e consumidor idempotente

**Decisão:** evento JSON versionado; publicador transacional usa SKIP LOCKED e espera ack do broker. Falhas preservam o evento com número de tentativas, próximo horário e erro. No consumidor, o eventId e a projeção confirmam na mesma transação local. O listener retorna ao Kafka depois desse commit.

**Motivo:** confirmar a transferência não pode depender da disponibilidade momentânea do broker. Uma falha após a publicação e antes do commit local permite entrega duplicada.

**Consequência:** entrega ao menos uma vez; sem promessa de exactly once entre sistemas. Um registro inválido é repetido indefinidamente e bloqueia a partição. DLQ com recuperação auditada é uma evolução. Há um lock de banco durante o envio Kafka, limitado pelos timeouts; para alto volume, considerar leases/claims e múltiplos workers.

## ADR 006 — Snapshot consistente na conciliação

**Decisão:** executar sincronicamente em REPEATABLE READ, agregando o razão e registrando divergências em tabelas próprias.

**Motivo:** contas, contadores e lançamentos devem refletir a mesma visão durante o run. Uma transferência concorrente não deve produzir uma falsa divergência.

**Consequência:** a primeira versão faz leitura integral e pode demorar em bases grandes. A evolução é execução assíncrona por partições com um corte contábil explícito. Conciliação registra evidência; não corrige saldo automaticamente.

## ADR 007 — Ambiente de demonstração e validação real

**Decisão:** Java 21, Spring Boot 3.5.16 e springdoc 2.8.17, com Maven Wrapper. Dois bancos PostgreSQL e Kafka em Compose; testes com PostgreSQL 17.7 e Kafka 3.9.1 via Testcontainers. A linha 3.5 permite demonstrar o stack solicitado com versões compatíveis; a versão do esqueleto inicial foi substituída por essa combinação verificada.

**Consequência:** os testes de integração exigem Docker. Não se substitui o PostgreSQL por H2 para comprovar locks, constraints e transações. Credenciais e emissão de JWT de demonstração só existem em local. Sem dependências AWS ou cobrança de serviços externos.

## Evoluções planejadas

1. Consultar cadastro legado de associados com WireMock e testes de timeout, indisponibilidade e contrato inválido.
2. Exportar extratos por SQS + worker + S3 privado, autorizando tanto a solicitação quanto a emissão da URL temporária.
3. Implantar imagens em ECS com secret manager, TLS, papéis de banco segregados, Kafka autenticado e métricas de lag.
4. Implementar estorno compensatório, DLQ/replay auditado e política de retenção/reconstrução de projeções.

Essas extensões não estão implementadas neste incremento.
