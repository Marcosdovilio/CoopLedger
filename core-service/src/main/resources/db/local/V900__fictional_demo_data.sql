-- Exclusively enabled by the local profile. No public API accepts an opening balance.
INSERT INTO accounts(id, owner_id, status, balance, created_at) VALUES
('00000000-0000-0000-0000-000000000001', 'demo-alice', 'ACTIVE', 100.00, now()),
('00000000-0000-0000-0000-000000000002', 'demo-bob', 'ACTIVE', 0.00, now());
INSERT INTO ledger_entries(id, account_id, transfer_id, entry_type, amount, description, created_at) VALUES
('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
NULL, 'OPENING_CREDIT', 100.00, 'Saldo inicial fictício para demonstração', now());
