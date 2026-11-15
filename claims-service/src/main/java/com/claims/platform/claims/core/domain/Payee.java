package com.claims.platform.claims.core.domain;

/** Who receives the settlement. Bank details are personal data: {@link #toString()} masks the account. */
public record Payee(String name, String bankCode, String accountNumber) {
    public Payee {
        if (isBlank(name) || isBlank(bankCode) || isBlank(accountNumber)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Payee name, bank code and account number are required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        String tail = accountNumber.length() <= 4 ? accountNumber : accountNumber.substring(accountNumber.length() - 4);
        return "Payee[name=" + name + ", bankCode=" + bankCode + ", account=****" + tail + "]";
    }
}
