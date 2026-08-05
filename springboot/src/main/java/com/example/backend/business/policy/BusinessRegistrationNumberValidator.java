package com.example.backend.business.policy;

/**
 * 사업자등록번호 10자리 체크섬 검증.
 * 국세청이 사용하는 공개 알고리즘으로, 형식이 유효한 번호인지만 확인한다
 * (실제로 등록된 사업자인지는 국세청 진위확인 API로 별도 확인해야 한다).
 */
public final class BusinessRegistrationNumberValidator {

    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private BusinessRegistrationNumberValidator() {
    }

    public static boolean isValidChecksum(String businessNumber) {
        if (businessNumber == null) {
            return false;
        }
        String digitsOnly = businessNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.length() != 10) {
            return false;
        }

        int[] digits = digitsOnly.chars().map(c -> c - '0').toArray();
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += digits[i] * WEIGHTS[i];
        }
        sum += (digits[8] * 5) / 10;
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == digits[9];
    }

    public static String normalize(String businessNumber) {
        return businessNumber == null ? null : businessNumber.replaceAll("[^0-9]", "");
    }
}
