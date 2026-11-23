package com.portfolio.insurance.claim;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimServiceTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "tutanak.pdf|tutanak.pdf",
            "C:\\Users\\x\\Desktop\\fotoğraf.jpg|fotoğraf.jpg",
            "../../etc/passwd.png|passwd.png",
            "\"quoted\".pdf|quoted.pdf",
            "''|evrak"})
    void keepsOnlyTheBareFileName(String original, String expected) {
        assertThat(ClaimService.cleanFileName(original)).isEqualTo(expected);
    }
}
