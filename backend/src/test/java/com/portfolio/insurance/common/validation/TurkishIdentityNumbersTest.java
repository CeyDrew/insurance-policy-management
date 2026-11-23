package com.portfolio.insurance.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TurkishIdentityNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"10000000146", "73098463538", "99079858926"})
    void acceptsValidTckn(String value) {
        assertThat(TurkishIdentityNumbers.isValidTckn(value)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "10000000147",  // wrong last check digit
            "10000000156",  // wrong tenth digit
            "01234567890",  // starts with zero
            "1234567890",   // too short
            "1000000014a"}) // not numeric
    void rejectsInvalidTckn(String value) {
        assertThat(TurkishIdentityNumbers.isValidTckn(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4519775531", "1730914733", "3157233719"})
    void acceptsValidVkn(String value) {
        assertThat(TurkishIdentityNumbers.isValidVkn(value)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4519775532", "451977553", "45197755311", "451977553x"})
    void rejectsInvalidVkn(String value) {
        assertThat(TurkishIdentityNumbers.isValidVkn(value)).isFalse();
    }
}
