package io.github.soleiman4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.soleiman4j.BalanceException.Reason.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryBalanceServiceTest {
    private InMemoryBalanceService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryBalanceService();
        service.createAccount("A", 1_000);
        service.createAccount("B", 500);
    }

    @Test
    void duplicateAccountCreationIsRejected() {
        assertEquals(ACCOUNT_ALREADY_EXISTS, assertThrows(BalanceException.class,
                () -> service.createAccount("A", 123)).reason());
    }

    @Test
    void repeatedTransfersDoNotMoveMoneyAgain() {
        service.transfer("A", "B", 300, "tx-3");
        service.transfer("A", "B", 300, "tx-3");
        assertEquals(700, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }

    @Test
    void transferOverflowDoesNotPartiallyDebitSource() {
        service.createAccount("MAX", Long.MAX_VALUE);
        BalanceException error = assertThrows(BalanceException.class,
                () -> service.transfer("A", "MAX", 1, "overflow-transfer"));
        assertEquals(BALANCE_OVERFLOW, error.reason());
        assertEquals(1_000, service.getBalance("A"));
        assertEquals(Long.MAX_VALUE, service.getBalance("MAX"));
    }

    @Test
    void repeatedCreditsDoNotIncreaseBalanceAgain() {
        for (int i = 0; i < 3; i++) {
            service.credit("A", 100, "tx-1");
        }
        assertEquals(1_100, service.getBalance("A"));
    }

    @Test
    void creditOverflowDoesNotChangeAccount() {
        service.createAccount("MAX", Long.MAX_VALUE);
        assertEquals(BALANCE_OVERFLOW, assertThrows(BalanceException.class,
                () -> service.credit("MAX", 1, "overflow-credit")).reason());
        assertEquals(Long.MAX_VALUE, service.getBalance("MAX"));
    }

    @Test
    void repeatedDebitsDoNotDecreaseBalanceAgain() {
        service.debit("A", 100, "tx-2");
        service.debit("A", 100, "tx-2");
        assertEquals(900, service.getBalance("A"));
    }

    @Test
    void rejectedDebitCannotMakeNegativeBalance() {
        assertEquals(INSUFFICIENT_FUNDS, assertThrows(BalanceException.class,
                () -> service.debit("A", 1_001, "too-big")).reason());
        assertEquals(1_000, service.getBalance("A"));
    }

    @Test
    void unknownAndSameAccountTransfersAreRejected() {
        assertEquals(UNKNOWN_ACCOUNT, assertThrows(BalanceException.class,
                () -> service.debit("missing", 1, "unknown")).reason());
        assertEquals(INVALID_INPUT, assertThrows(BalanceException.class,
                () -> service.transfer("A", "A", 1, "self")).reason());
        assertEquals(UNKNOWN_ACCOUNT, assertThrows(BalanceException.class,
                () -> service.transfer("A", "missing", 1, "unknown-destination")).reason());
    }

    @Test
    void conflictingTransactionIdIsRejected() {
        service.credit("A", 100, "shared-id");
        BalanceException error = assertThrows(BalanceException.class,
                () -> service.debit("A", 100, "shared-id"));
        assertEquals(TRANSACTION_ID_CONFLICT, error.reason());
        assertEquals(1_100, service.getBalance("A"));
    }

    @Test
    void invalidAmountsAndIdsAreRejected() {
        assertEquals(INVALID_INPUT, assertThrows(BalanceException.class,
                () -> service.credit("A", 0, "bad-zero")).reason());
        assertEquals(INVALID_INPUT, assertThrows(BalanceException.class,
                () -> service.debit("A", -1, "bad-negative")).reason());
        assertEquals(INVALID_INPUT, assertThrows(BalanceException.class,
                () -> service.credit("  ", 100, "bad-account")).reason());
        assertEquals(INVALID_INPUT, assertThrows(BalanceException.class,
                () -> service.credit("A", 1, " ")).reason());
    }

    @Test
    void creditDebitAndTransferWork() {
        service.credit("A", 250, "tx-credit");
        service.debit("A", 100, "tx-debit");
        service.transfer("A", "B", 300, "tx-transfer");
        assertEquals(850, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }

    @Test
    void failedDebitIsRememberedEvenIfBalanceLaterChanges() {
        BalanceException original = assertThrows(BalanceException.class,
                () -> service.debit("B", 700, "failed-tx"));
        assertEquals(INSUFFICIENT_FUNDS, original.reason());
        service.credit("B", 500, "new-credit");
        BalanceException retried = assertThrows(BalanceException.class,
                () -> service.debit("B", 700, "failed-tx"));
        assertEquals(INSUFFICIENT_FUNDS, retried.reason());
        assertEquals(1_000, service.getBalance("B"));
    }
}
