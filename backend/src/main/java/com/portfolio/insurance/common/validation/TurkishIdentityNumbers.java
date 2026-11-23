package com.portfolio.insurance.common.validation;

/**
 * Checksum validation for Turkish identity numbers.
 * <ul>
 *   <li>TCKN (T.C. Kimlik No, individuals): 11 digits, first digit non-zero, two check digits.</li>
 *   <li>VKN (Vergi Kimlik No, companies): 10 digits, last digit is a weighted checksum.</li>
 * </ul>
 */
public final class TurkishIdentityNumbers {

    private TurkishIdentityNumbers() {
    }

    public static boolean isValidTckn(String value) {
        if (value == null || !value.matches("[1-9]\\d{10}")) {
            return false;
        }
        int[] d = digits(value);
        int odd = d[0] + d[2] + d[4] + d[6] + d[8];
        int even = d[1] + d[3] + d[5] + d[7];
        int tenth = Math.floorMod(odd * 7 - even, 10);
        int eleventh = (odd + even + d[9]) % 10;
        return d[9] == tenth && d[10] == eleventh;
    }

    public static boolean isValidVkn(String value) {
        if (value == null || !value.matches("\\d{10}")) {
            return false;
        }
        int[] d = digits(value);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int tmp = (d[i] + 9 - i) % 10;
            int v = (tmp * (1 << (9 - i))) % 9;
            if (tmp != 0 && v == 0) {
                v = 9;
            }
            sum += v;
        }
        return (10 - sum % 10) % 10 == d[9];
    }

    private static int[] digits(String value) {
        return value.chars().map(c -> c - '0').toArray();
    }
}
