package com.portfolio.insurance.common.plsql;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns application errors raised by the PL/SQL packages into exceptions the API understands.
 * <p>
 * PL/SQL raises {@code RAISE_APPLICATION_ERROR(-200xx, 'CODE|Turkish message')} (see PKG_ERRORS).
 * -20001 becomes a {@link ResourceNotFoundException} (404), every other -20xxx a {@link BusinessException}
 * (422) carrying the code. Other database errors are left untouched.
 */
public final class PlsqlErrors {

    static final int NOT_FOUND = 20001;
    private static final int APPLICATION_ERROR_MIN = 20000;
    private static final int APPLICATION_ERROR_MAX = 20999;
    /** First line of an application error, e.g. "ORA-20004: POLICY_NOT_QUOTE|Sadece teklif ...". */
    private static final Pattern MESSAGE = Pattern.compile("ORA-2\\d{4}: ([^\\r\\n]*)");
    private static final String GENERIC_CODE = "DATABASE_RULE";

    private PlsqlErrors() {
    }

    /** Runs a database call and translates PL/SQL application errors. */
    public static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException ex) {
            throw translate(ex);
        }
    }

    public static RuntimeException translate(DataAccessException ex) {
        SQLException sqlException = findSqlException(ex);
        if (sqlException == null) {
            return ex;
        }
        int errorCode = sqlException.getErrorCode();
        if (errorCode < APPLICATION_ERROR_MIN || errorCode > APPLICATION_ERROR_MAX) {
            return ex;
        }

        Matcher matcher = MESSAGE.matcher(String.valueOf(sqlException.getMessage()));
        String text = matcher.find() ? matcher.group(1).trim() : String.valueOf(sqlException.getMessage());
        int separator = text.indexOf('|');
        String code = separator > 0 ? text.substring(0, separator) : GENERIC_CODE;
        String message = separator > 0 ? text.substring(separator + 1) : text;

        return errorCode == NOT_FOUND ? new ResourceNotFoundException(message) : new BusinessException(code, message);
    }

    private static SQLException findSqlException(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql;
            }
        }
        return null;
    }
}
