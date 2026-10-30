CREATE FUNCTION reject_financial_history_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Financial history is append-only: % on %', TG_OP, TG_TABLE_NAME;
END;
$$;
CREATE TRIGGER ledger_is_append_only BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_financial_history_mutation();
CREATE TRIGGER transfers_are_immutable BEFORE UPDATE OR DELETE ON transfers
    FOR EACH ROW EXECUTE FUNCTION reject_financial_history_mutation();

-- Deferred until commit: intermediate debit-only state is allowed inside the transaction.
CREATE FUNCTION verify_transfer_postings() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    operation_id UUID;
    valid_postings INTEGER;
    total_postings INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'transfers' THEN operation_id := NEW.id;
    ELSE operation_id := NEW.transfer_id;
    END IF;
    IF operation_id IS NULL THEN RETURN NEW; END IF;
    SELECT count(*), count(*) FILTER (WHERE l.amount = t.amount AND
        ((l.entry_type = 'DEBIT' AND l.account_id = t.source_account_id)
          OR (l.entry_type = 'CREDIT' AND l.account_id = t.destination_account_id)))
    INTO total_postings, valid_postings
    FROM ledger_entries l JOIN transfers t ON t.id = l.transfer_id WHERE t.id = operation_id;
    IF total_postings <> 2 OR valid_postings <> 2 THEN
        RAISE EXCEPTION 'Transfer % must have exactly two matching postings', operation_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER balanced_transfer AFTER INSERT ON transfers
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_transfer_postings();
CREATE CONSTRAINT TRIGGER balanced_postings AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_transfer_postings();

CREATE FUNCTION verify_completed_idempotency() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM idempotency_keys WHERE actor_id = NEW.actor_id
        AND request_key = NEW.request_key AND transfer_id IS NULL) THEN
        RAISE EXCEPTION 'Idempotency reservation must complete in the same transaction';
    END IF;
    RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER completed_idempotency AFTER INSERT OR UPDATE ON idempotency_keys
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_completed_idempotency();
