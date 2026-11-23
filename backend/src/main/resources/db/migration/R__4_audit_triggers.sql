-- Audit triggers: every insert/update/delete on POLICY and CLAIM is written to AUDIT_LOG as JSON snapshots
-- of the business-relevant columns. changed_by is taken from the row's audit columns (filled by the
-- application or the PL/SQL packages); deletes fall back to the database session user.

CREATE OR REPLACE TRIGGER trg_policy_audit
    AFTER INSERT OR UPDATE OR DELETE ON policy
    FOR EACH ROW
DECLARE
    l_old       CLOB;
    l_new       CLOB;
    l_operation audit_log.operation%TYPE;
BEGIN
    -- INSERTING/UPDATING/DELETING are PL/SQL booleans and cannot be used inside SQL statements.
    l_operation := CASE WHEN INSERTING THEN 'INSERT' WHEN UPDATING THEN 'UPDATE' ELSE 'DELETE' END;

    IF NOT INSERTING THEN
        SELECT JSON_OBJECT(
                   'policyNumber'     VALUE :OLD.policy_number,
                   'status'           VALUE :OLD.status,
                   'startDate'        VALUE :OLD.start_date,
                   'endDate'          VALUE :OLD.end_date,
                   'ncdStep'          VALUE :OLD.ncd_step,
                   'netPremium'       VALUE :OLD.net_premium,
                   'grossPremium'     VALUE :OLD.gross_premium,
                   'installmentCount' VALUE :OLD.installment_count,
                   'cancelReason'     VALUE :OLD.cancel_reason,
                   'refundAmount'     VALUE :OLD.refund_amount
                   RETURNING CLOB)
          INTO l_old
          FROM dual;
    END IF;
    IF NOT DELETING THEN
        SELECT JSON_OBJECT(
                   'policyNumber'     VALUE :NEW.policy_number,
                   'status'           VALUE :NEW.status,
                   'startDate'        VALUE :NEW.start_date,
                   'endDate'          VALUE :NEW.end_date,
                   'ncdStep'          VALUE :NEW.ncd_step,
                   'netPremium'       VALUE :NEW.net_premium,
                   'grossPremium'     VALUE :NEW.gross_premium,
                   'installmentCount' VALUE :NEW.installment_count,
                   'cancelReason'     VALUE :NEW.cancel_reason,
                   'refundAmount'     VALUE :NEW.refund_amount
                   RETURNING CLOB)
          INTO l_new
          FROM dual;
    END IF;

    INSERT INTO audit_log (table_name, record_id, operation, old_values, new_values, changed_by)
    VALUES ('POLICY',
            COALESCE(:NEW.id, :OLD.id),
            l_operation,
            l_old,
            l_new,
            COALESCE(:NEW.updated_by, :NEW.created_by, SYS_CONTEXT('USERENV', 'SESSION_USER')));
END;
/

CREATE OR REPLACE TRIGGER trg_claim_audit
    AFTER INSERT OR UPDATE OR DELETE ON claim
    FOR EACH ROW
DECLARE
    l_old       CLOB;
    l_new       CLOB;
    l_operation audit_log.operation%TYPE;
BEGIN
    -- INSERTING/UPDATING/DELETING are PL/SQL booleans and cannot be used inside SQL statements.
    l_operation := CASE WHEN INSERTING THEN 'INSERT' WHEN UPDATING THEN 'UPDATE' ELSE 'DELETE' END;

    IF NOT INSERTING THEN
        SELECT JSON_OBJECT(
                   'claimNumber'     VALUE :OLD.claim_number,
                   'policyId'        VALUE :OLD.policy_id,
                   'status'          VALUE :OLD.status,
                   'estimatedAmount' VALUE :OLD.estimated_amount,
                   'approvedAmount'  VALUE :OLD.approved_amount,
                   'paidAmount'      VALUE :OLD.paid_amount,
                   'handlerId'       VALUE :OLD.handler_id,
                   'decisionNote'    VALUE :OLD.decision_note
                   RETURNING CLOB)
          INTO l_old
          FROM dual;
    END IF;
    IF NOT DELETING THEN
        SELECT JSON_OBJECT(
                   'claimNumber'     VALUE :NEW.claim_number,
                   'policyId'        VALUE :NEW.policy_id,
                   'status'          VALUE :NEW.status,
                   'estimatedAmount' VALUE :NEW.estimated_amount,
                   'approvedAmount'  VALUE :NEW.approved_amount,
                   'paidAmount'      VALUE :NEW.paid_amount,
                   'handlerId'       VALUE :NEW.handler_id,
                   'decisionNote'    VALUE :NEW.decision_note
                   RETURNING CLOB)
          INTO l_new
          FROM dual;
    END IF;

    INSERT INTO audit_log (table_name, record_id, operation, old_values, new_values, changed_by)
    VALUES ('CLAIM',
            COALESCE(:NEW.id, :OLD.id),
            l_operation,
            l_old,
            l_new,
            COALESCE(:NEW.updated_by, :NEW.created_by, SYS_CONTEXT('USERENV', 'SESSION_USER')));
END;
/
