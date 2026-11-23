-- Foreign key without an index: deleting or updating a PAYMENT row made Oracle scan COLLECTION_RESULT for every
-- row (found while resetting a one-million-line measurement). Index the referencing column.
CREATE INDEX ix_collection_result_payment ON collection_result (payment_id);
