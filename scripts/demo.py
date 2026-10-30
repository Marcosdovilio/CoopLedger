#!/usr/bin/env python3
"""Demonstração local sem dependências Python externas."""
import argparse
import json
import re
import time
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

SOURCE = "00000000-0000-0000-0000-000000000001"
DESTINATION = "00000000-0000-0000-0000-000000000002"


def request(base, method, path, token=None, body=None, key=None, expected=(200,)):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    data = body if isinstance(body, bytes) else (json.dumps(body).encode() if body is not None else None)
    call = Request(base + path, method=method, headers=headers, data=data)
    try:
        response = urlopen(call, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        status = response.code
        payload = json.loads(response.read(), parse_float=Decimal)
    if status not in expected:
        raise RuntimeError(f"{method} {path}: HTTP {status}: {payload}")
    return status, payload


def token(base, user):
    return request(base, "POST", "/auth/token",
                   body={"username": user, "password": user + "-demo"})[1]["accessToken"]


def transfer_body(amount):
    fields = json.dumps({"sourceAccountId": SOURCE, "destinationAccountId": DESTINATION,
                         "description": "Demonstração CoopLedger"})
    return (fields[:-1] + f', "amount": {amount}}}').encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core", default="http://localhost:8080")
    parser.add_argument("--operations", default="http://localhost:8082")
    parser.add_argument("--amount", default="30.00")
    parser.add_argument("--key", default="demo-transfer-30-v1")
    parser.add_argument("--skip-operations", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"(0|[1-9][0-9]{0,16})(\.[0-9]{1,2})?", args.amount) or Decimal(args.amount) <= 0:
        parser.error("--amount deve ser positivo, com até duas casas decimais")
    amount = Decimal(args.amount)
    alice, bob, ops = (token(args.core, user) for user in ("alice", "bob", "ops"))
    before = request(args.core, "GET", f"/accounts/{SOURCE}/balance", alice)[1]["availableBalance"]
    status, original = request(args.core, "POST", "/transfers", alice, transfer_body(amount), args.key, (200, 201))
    _, replay = request(args.core, "POST", "/transfers", alice, transfer_body(amount), args.key)
    assert replay == original, "A repetição deve devolver exatamente a operação original"
    request(args.core, "POST", "/transfers", alice, transfer_body(amount + Decimal("0.01")), args.key, (409,))
    request(args.core, "GET", f"/accounts/{SOURCE}/statement", bob, expected=(403,))
    after = request(args.core, "GET", f"/accounts/{SOURCE}/balance", alice)[1]["availableBalance"]
    expected_balance = before - amount if status == 201 else before
    assert after == expected_balance
    statement = request(args.core, "GET", f"/accounts/{SOURCE}/statement?size=1", alice)[1]
    run = request(args.core, "POST", "/reconciliation-runs", ops, expected=(201,))[1]
    assert run["divergenceCount"] == 0, f"Conciliação detectou divergências: {run}"
    print(f"Transferência {original['id']}: HTTP {status}, R$ {amount}")
    print(f"Origem: R$ {before} → R$ {after}; repetição sem novo débito")
    print(f"Conflito de chave: 409; extrato de outro titular: 403; lançamentos da origem: {statement['totalElements']}")
    print(f"Conciliação: {run['accountsChecked']} contas, {run['divergenceCount']} divergências")
    if not args.skip_operations:
        deadline = time.monotonic() + 45
        while True:
            code, view = request(args.operations, "GET", f"/operations/transfers/{original['id']}", ops, expected=(200, 404))
            if code == 200:
                assert view["transferId"] == original["id"]
                print("Evento consumido e transferência disponível no banco de operações")
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Visão de operações não atualizou em 45s; verifique o Kafka e os logs")
            time.sleep(1)


if __name__ == "__main__":
    main()
