package io.github.soleiman4j;

public final class BalanceException extends RuntimeException {
    public enum Reason {
        INVALID_INPUT,
        UNKNOWN_ACCOUNT,
        ACCOUNT_ALREADY_EXISTS,
        INSUFFICIENT_FUNDS,
        TRANSACTION_ID_CONFLICT,
        BALANCE_OVERFLOW
    }

    private final Reason reason;

    public BalanceException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}