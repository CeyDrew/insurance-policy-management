package com.portfolio.insurance;

import java.util.concurrent.ThreadLocalRandom;

/** Random but valid identifiers, so integration tests never collide with seed data or each other. */
public final class TestData {

    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";

    private TestData() {
    }

    public static String tckn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] d = new int[11];
        d[0] = random.nextInt(1, 10);
        for (int i = 1; i < 9; i++) {
            d[i] = random.nextInt(10);
        }
        int odd = d[0] + d[2] + d[4] + d[6] + d[8];
        int even = d[1] + d[3] + d[5] + d[7];
        d[9] = Math.floorMod(odd * 7 - even, 10);
        d[10] = (odd + even + d[9]) % 10;
        return join(d);
    }

    public static String vkn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] d = new int[10];
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            d[i] = random.nextInt(10);
            int tmp = (d[i] + 9 - i) % 10;
            int v = (tmp * (1 << (9 - i))) % 9;
            sum += (tmp != 0 && v == 0) ? 9 : v;
        }
        d[9] = (10 - sum % 10) % 10;
        return join(d);
    }

    public static String plate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "%02d T %04d".formatted(random.nextInt(1, 82), random.nextInt(1000, 10000));
    }

    public static String chassis() {
        StringBuilder vin = new StringBuilder("TST");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (vin.length() < 17) {
            vin.append(VIN_CHARS.charAt(random.nextInt(VIN_CHARS.length())));
        }
        return vin.toString();
    }

    private static String join(int[] digits) {
        StringBuilder sb = new StringBuilder();
        for (int digit : digits) {
            sb.append(digit);
        }
        return sb.toString();
    }
}
