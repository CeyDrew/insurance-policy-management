-- Application errors raised from PL/SQL.
--
-- Every business error is raised as RAISE_APPLICATION_ERROR(<number>, '<CODE>|<message>'):
--   * <number> classifies the error (not found, validation, missing tariff, invalid state),
--   * <CODE>   is a stable machine-readable identifier (e.g. POLICY_NOT_QUOTE),
--   * <message> is the Turkish text shown to the user.
-- The Java layer (PlsqlErrors) parses this format and turns it into a BusinessException.

CREATE OR REPLACE PACKAGE pkg_errors AS

    c_not_found        CONSTANT PLS_INTEGER := -20001;
    c_validation       CONSTANT PLS_INTEGER := -20002;
    c_tariff_not_found CONSTANT PLS_INTEGER := -20003;
    c_invalid_state    CONSTANT PLS_INTEGER := -20004;

    PROCEDURE raise_error(p_number IN PLS_INTEGER, p_code IN VARCHAR2, p_message IN VARCHAR2);

END pkg_errors;
/

CREATE OR REPLACE PACKAGE BODY pkg_errors AS

    PROCEDURE raise_error(p_number IN PLS_INTEGER, p_code IN VARCHAR2, p_message IN VARCHAR2) IS
    BEGIN
        RAISE_APPLICATION_ERROR(p_number, p_code || '|' || p_message);
    END raise_error;

END pkg_errors;
/
