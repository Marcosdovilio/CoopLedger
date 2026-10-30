# Demonstração para entrevista

## Preparação

```sh
docker compose --profile monitoring up --build -d --wait
python3 scripts/demo.py
```

Na primeira execução, Alice começa com R$ 100 e Bob com zero. O script usa a mesma chave nas repetições, de modo que rodá-lo novamente não faz novo débito.

Mostre o core no Swagger, a resposta com ID de operação, o extrato e a projeção em operações. Explique que o lançamento inicial é parte do razão e que os dois bancos têm responsabilidades diferentes.

## Concorrência e rollback

```sh
./mvnw -pl core-service -am -Dit.test=CoreIntegrationIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Os métodos failureAfterFlushedDebitRollsBackEverything, concurrentSpendingApprovesOnlyOneEightyReaisTransfer e simultaneousSameKeyMovesMoneyOnce cobrem os cenários centrais. A falha é injetada com Mockito depois do flush do débito, antes de aplicar o crédito. As verificações usam PostgreSQL real e as requisições concorrentes usam threads independentes.

## Desligar e recuperar o Kafka

Com o cenário inicial já demonstrado, ainda há R$ 70 na origem:

```sh
docker compose stop kafka
python3 scripts/demo.py --amount 10.00 --key demo-kafka-recovery-v1 --skip-operations
docker compose exec core-db psql -U coopledger -d coopledger -c \
  "SELECT id, attempts, published_at, next_attempt_at FROM outbox_events ORDER BY created_at;"
docker compose start kafka
python3 scripts/demo.py --amount 10.00 --key demo-kafka-recovery-v1
```

O débito confirma com o broker desligado. O script espera a projeção após a recuperação. O backoff cresce até 256 segundos, então, após indisponibilidade longa, a projeção pode levar mais que os 45 segundos de espera do script; nesse caso execute novamente, preservando a mesma chave.

No Grafana, mostre quantidade de eventos pendentes e idade do mais antigo antes e depois da recuperação. Ao reiniciar operações, o mesmo grupo Kafka retoma o offset; se um evento voltar a ser entregue, o eventId evita nova projeção.

## Conciliação de divergência controlada

Execute preferencialmente o teste reconciliationRecordsDivergenceAndDoesNotChangeBalance; ele altera apenas dados efêmeros. Uma demonstração manual em local pode introduzir e depois desfazer uma diferença de R$ 50:

```sh
docker compose exec core-db psql -U coopledger -d coopledger -c \
  "UPDATE accounts SET balance = balance + 50.00 WHERE id = '00000000-0000-0000-0000-000000000001';"
```

Autentique como ops e execute POST /reconciliation-runs pelo Swagger. Consulte /reconciliation-runs/{id}/divergences: a diferença registrada será R$ 50.

```sh
docker compose exec core-db psql -U coopledger -d coopledger -c \
  "UPDATE accounts SET balance = balance - 50.00 WHERE id = '00000000-0000-0000-0000-000000000001';"
```

Uma nova conciliação deve ficar limpa. A evidência do run anterior permanece armazenada. Esse SQL é uma intervenção controlada na base fictícia, não uma funcionalidade da API.

## Reset e diagnóstico

`docker compose down` preserva dados. Para recomeçar **apagando os volumes de demonstração**, execute conscientemente:

```sh
docker compose --profile monitoring down -v
docker compose --profile monitoring up --build -d --wait
```

Diagnóstico: docker compose ps; docker compose logs core operations kafka; GET /actuator/health. Kafka usa localhost:9092 para clientes no host e kafka:19092 entre contêineres.

## Descrição para currículo

CoopLedger — projeto autoral de back-end para domínio financeiro: APIs de contas e transferências com Java, Spring Boot e PostgreSQL, incluindo controle de concorrência, idempotência, eventos com Kafka, conciliação e testes automatizados.

Esse texto descreve prática neste projeto. Não representa experiência profissional em instituição financeira.
