package com.portfolio.insurance.common.plsql;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlsqlErrorsTest {

    @Test
    void applicationErrorBecomesBusinessExceptionWithCode() {
        RuntimeException translated = PlsqlErrors.translate(oracleError(20004, """
                ORA-20004: POLICY_NOT_QUOTE|Sadece teklif durumundaki kayıtlar poliçeleştirilebilir.
                ORA-06512: at "INSURANCE.PKG_ERRORS", line 5
                ORA-06512: at "INSURANCE.PKG_POLICY", line 98"""));

        assertThat(translated).isInstanceOf(BusinessException.class)
                .hasMessage("Sadece teklif durumundaki kayıtlar poliçeleştirilebilir.");
        assertThat(((BusinessException) translated).getCode()).isEqualTo("POLICY_NOT_QUOTE");
    }

    @Test
    void notFoundErrorBecomesResourceNotFound() {
        RuntimeException translated = PlsqlErrors.translate(
                oracleError(20001, "ORA-20001: POLICY_NOT_FOUND|Poliçe bulunamadı: 42\nORA-06512: at line 1"));

        assertThat(translated).isInstanceOf(ResourceNotFoundException.class).hasMessage("Poliçe bulunamadı: 42");
    }

    @Test
    void applicationErrorWithoutCodeGetsGenericCode() {
        RuntimeException translated = PlsqlErrors.translate(oracleError(20099, "ORA-20099: Something went wrong"));

        assertThat(((BusinessException) translated).getCode()).isEqualTo("DATABASE_RULE");
        assertThat(translated).hasMessage("Something went wrong");
    }

    @Test
    void otherDatabaseErrorsAreLeftUntouched() {
        DataAccessException constraint = new DataIntegrityViolationException("unique",
                new SQLException("ORA-00001: unique constraint violated", "23000", 1));

        assertThat(PlsqlErrors.translate(constraint)).isSameAs(constraint);
    }

    @Test
    void callTranslatesThrownErrors() {
        assertThatThrownBy(() -> PlsqlErrors.call(() -> {
            throw oracleError(20003, "ORA-20003: TARIFF_NOT_FOUND|Sürücü yaşı (17) için tarife bulunmuyor.");
        })).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("TARIFF_NOT_FOUND");
    }

    private static DataAccessException oracleError(int code, String message) {
        return new UncategorizedSQLException("call", "BEGIN ... END;", new SQLException(message, "72000", code));
    }
}
