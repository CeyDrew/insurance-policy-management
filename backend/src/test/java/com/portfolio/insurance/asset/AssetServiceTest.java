package com.portfolio.insurance.asset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssetServiceTest {

    @ParameterizedTest
    @CsvSource({
            "34abc123, 34 ABC 123",
            "34  ABC   123, 34 ABC 123",
            "06 a 1234, 06 A 1234",
            "35 ZT 45, 35 ZT 45"})
    void normalizesPlateNumbers(String input, String expected) {
        assertThat(AssetService.normalizePlate(input)).isEqualTo(expected);
    }
}
